package com.polyauth.structured.application.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polyauth.structured.application.decision.DecisionService;
import com.polyauth.structured.common.enums.AccountStatus;
import com.polyauth.structured.common.enums.ErrorCode;
import com.polyauth.structured.common.exception.BusinessException;
import com.polyauth.structured.infrastructure.crypto.CryptoComponent;
import com.polyauth.structured.infrastructure.persistence.entity.IamLog;
import com.polyauth.structured.infrastructure.persistence.entity.IamOrg;
import com.polyauth.structured.infrastructure.persistence.entity.IamPermission;
import com.polyauth.structured.infrastructure.persistence.entity.IamSystem;
import com.polyauth.structured.infrastructure.persistence.entity.IamUser;
import com.polyauth.structured.infrastructure.persistence.entity.IamApi;
import com.polyauth.structured.infrastructure.persistence.repository.IamApiRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamLogRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamPermissionRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamOrgRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamSystemRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamUserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * 面向外部业务系统的统一身份查询服务。
 *
 * <p>该服务只暴露外部契约需要的身份、目录和权限事实，内部管理端接口仍使用原服务。
 */
@Service
@RequiredArgsConstructor
public class IdentityService {

    private static final String EXT_AUDIT_DECISION_ID = "EXT_AUDIT";
    private static final String LOG_TYPE_ACTION = "ACTION";
    private static final int MAX_LOG_TARGET_LENGTH = 255;
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> AUDIT_QUERY_PERMISSIONS = Set.of("AV", "MLG");
    private static final Set<String> AUDIT_QUERY_ROLES = Set.of("admin", "teacher");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final IamUserRepository userRepository;
    private final IamOrgRepository orgRepository;
    private final IamApiRepository apiRepository;
    private final IamSystemRepository systemRepository;
    private final IamPermissionRepository permissionRepository;
    private final DecisionService decisionService;
    private final CryptoComponent cryptoComponent;
    private final IamLogRepository logRepository;
    private final ObjectMapper objectMapper;

    /**
     * 查询当前用户及其在目标系统内的角色和前端权限码。
     */
    @Transactional(readOnly = true)
    public IdentityProfile getCurrentUser(Long userId, String systemCode) {
        IamUser user = requireActiveUser(userId);
        IamSystem system = requireSystem(systemCode);
        Set<Long> permissionIds = decisionService.getUserPermissions(userId, system.getSystemCode());
        List<String> permissionCodes = permissionRepository.findAllById(permissionIds).stream()
                .filter(permission -> permission.getIsDeleted() == 0)
                .filter(permission -> system.getId().equals(permission.getSystemId()))
                .map(IamPermission::getPermissionCode)
                .sorted()
                .toList();
        List<String> roles = decisionService.getUserRoleCodes(userId).stream()
                .map(this::toExternalRole)
                .filter(role -> role != null)
                .distinct()
                .sorted()
                .toList();
        return new IdentityProfile(user.getUserNo(), roles, permissionCodes,
                cryptoComponent.decrypt(user.getNameEnc()), user.getUserNo());
    }

    /**
     * 判断用户是否拥有目标系统中的指定前端权限码。
     */
    @Transactional(readOnly = true)
    public boolean hasPermissionCode(Long userId, String systemCode, String permissionCode) {
        requireActiveUser(userId);
        return hasPermissionCodeForActiveUser(userId, systemCode, permissionCode);
    }

    private boolean hasPermissionCodeForActiveUser(
            Long userId, String systemCode, String permissionCode) {
        IamSystem system = requireSystem(systemCode);
        return permissionRepository.findBySystemIdAndPermissionCode(system.getId(), permissionCode)
                .filter(permission -> permission.getIsDeleted() == 0)
                .filter(permission -> IamPermission.STATUS_ACTIVE.equals(permission.getStatus()))
                .map(permission -> decisionService.hasPermission(
                        userId, system.getSystemCode(), permission.getId()))
                .orElse(false);
    }

    /**
     * 按层级分页查询部门。{@code parentId} 为空查根部门，否则只查直接下级。
     */
    @Transactional(readOnly = true)
    public IdentityPageSlice<IdentityDepartmentData> pageDepartments(
            String keyword, Long parentId, int page, int pageSize) {
        List<IamOrg> departments;
        if (parentId == null) {
            departments = orgRepository.findByParentIdIsNull();
        } else {
            requireDepartment(parentId);
            departments = orgRepository.findByParentId(parentId);
        }
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<IamOrg> filtered = departments.stream()
                .filter(department -> department.getIsDeleted() == 0)
                .filter(department -> IamOrg.STATUS_ACTIVE.equals(department.getStatus()))
                .filter(department -> normalizedKeyword.isEmpty()
                        || department.getOrgCode().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                        || department.getOrgName().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .sorted(Comparator.comparing(IamOrg::getSortNo).thenComparing(IamOrg::getId))
                .toList();
        return pageOf(filtered, page, pageSize, department -> new IdentityDepartmentData(
                department.getId(),
                department.getOrgCode(),
                department.getOrgName(),
                department.getParentId(),
                hasActiveChildren(department.getId())));
    }

    /**
     * 按 OpenAPI operationId 判断用户是否具有目标 API 的全局调用权限。
     */
    @Transactional(readOnly = true)
    public boolean hasApiOperation(Long userId, String systemCode, String operationId) {
        requireActiveUser(userId);
        IamSystem system = requireSystem(systemCode);
        IamApi api = apiRepository.findBySystemIdAndApiCode(system.getId(), operationId)
                .filter(item -> item.getIsDeleted() == 0)
                .filter(item -> IamApi.STATUS_ACTIVE.equals(item.getStatus()))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "没有找到对应的 API 操作。"));
        return api.getPermissionId() != null
                && decisionService.hasPermission(userId, system.getSystemCode(), api.getPermissionId());
    }

    /**
     * 分页查询部门下的统一身份用户，支持工号、学号或姓名模糊匹配。
     */
    @Transactional(readOnly = true)
    public IdentityPageSlice<IdentityDepartmentUserData> pageDepartmentUsers(
            Long departmentId, String keyword, int page, int pageSize) {
        IamOrg department = requireDepartment(departmentId);
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<IamUser> filtered = userRepository.findByOrgId(departmentId).stream()
                .filter(user -> user.getIsDeleted() == 0)
                .filter(user -> normalizedKeyword.isEmpty()
                        || user.getUserNo().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                        || cryptoComponent.decrypt(user.getNameEnc())
                        .toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .sorted(Comparator.comparing(IamUser::getUserNo))
                .toList();
        return pageOf(filtered, page, pageSize, user -> new IdentityDepartmentUserData(
                user.getUserNo(),
                user.getUserNo(),
                cryptoComponent.decrypt(user.getNameEnc()),
                department.getId(),
                department.getOrgName()));
    }

    /**
     * 查询统一身份用户。仅具备目标系统 {@code TEC} 权限的管理员可使用。
     */
    @Transactional(readOnly = true)
    public IdentityUserData lookupUser(Long operatorId, String systemCode, String identityUserId) {
        requireActiveUser(operatorId);
        if (!hasPermissionCodeForActiveUser(operatorId, systemCode, "TEC")) {
            throw new BusinessException(ErrorCode.NO_FUNCTION_PERM, "你没有添加教师的权限。");
        }
        IamUser user = userRepository.findByUserNo(identityUserId)
                .filter(item -> item.getIsDeleted() == 0)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "统一身份认证系统中没有找到该用户。"));
        return new IdentityUserData(user.getUserNo(), user.getUserNo(),
                cryptoComponent.decrypt(user.getNameEnc()));
    }

    /**
     * 分页查询外部业务系统写入的统一身份审计日志。
     */
    @Transactional(readOnly = true)
    public IdentityPageSlice<IdentityAuditLogData> pageAuditLogs(
            Long operatorId, String systemCode, String actor, Long actorId,
            String target, OffsetDateTime from, OffsetDateTime to, int page, int pageSize) {
        requireActiveUser(operatorId);
        IamSystem system = requireSystem(systemCode);
        requireAuditQueryAccess(operatorId, system);
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "日志开始时间不能晚于结束时间");
        }
        LocalDateTime fromTime = toShanghaiLocalDateTime(from);
        LocalDateTime toTime = toShanghaiLocalDateTime(to);
        String normalizedActor = normalizeKeyword(actor);
        String normalizedTarget = normalizeKeyword(target);

        List<IamLog> candidates = logRepository.findAll(
                buildExternalAuditSpecification(system.getSystemCode(), actorId, fromTime, toTime),
                Sort.by(Sort.Direction.DESC, "operateTime", "logId"));
        Map<Long, ResolvedAuditActor> actorCache = new HashMap<>();
        List<IdentityAuditLogData> matched = new ArrayList<>();
        for (IamLog entry : candidates) {
            ResolvedAuditActor resolvedActor = resolveAuditActor(
                    entry.getUserId(), entry.getUsername(), actorCache);
            if (normalizedActor != null && !matchesActor(resolvedActor, entry.getUsername(), normalizedActor)) {
                continue;
            }
            IdentityAuditLogData data = toAuditLogData(entry, resolvedActor.actor());
            if (normalizedTarget != null && !containsIgnoreCase(data.target(), normalizedTarget)) {
                continue;
            }
            matched.add(data);
        }
        return pageOf(matched, page, pageSize, Function.identity());
    }

    /**
     * 按幂等键写入外部业务系统审计日志；同一系统内相同 requestId 重试时直接返回原记录。
     */
    @Transactional
    public IdentityAuditLogData writeAuditLog(
            Long userId, String systemCode, String requestId, String type,
            String objectType, String objectId, String target,
            Map<String, Object> before, Map<String, Object> after, String clientIp) {
        IamUser actor = requireActiveUser(userId);
        IamSystem system = requireSystem(systemCode);
        String normalizedRequestId = requireNonBlank(requestId, "请求幂等键不能为空");
        String normalizedType = requireNonBlank(type, "审计事件类型不能为空");
        String normalizedObjectType = requireNonBlank(objectType, "审计对象类型不能为空");
        String normalizedObjectId = requireNonBlank(objectId, "审计对象标识不能为空");
        String normalizedTarget = requireNonBlank(target, "审计对象摘要不能为空");

        Optional<IamLog> existing = logRepository.findFirstBySystemCodeAndDecisionIdAndRequestId(
                system.getSystemCode(), EXT_AUDIT_DECISION_ID, normalizedRequestId);
        Map<Long, ResolvedAuditActor> actorCache = new HashMap<>();
        if (existing.isPresent()) {
            IamLog entry = existing.get();
            return toAuditLogData(entry,
                    resolveAuditActor(entry.getUserId(), entry.getUsername(), actorCache).actor());
        }

        String termId = "term".equals(normalizedObjectType) ? normalizedObjectId : null;
        String courseId = "course".equals(normalizedObjectType) ? normalizedObjectId : null;
        String classId = "class".equals(normalizedObjectType) ? normalizedObjectId : null;

        IamLog entry = new IamLog();
        entry.setLogType(LOG_TYPE_ACTION);
        entry.setUserId(actor.getId());
        entry.setUsername(actor.getUserNo());
        entry.setAction(normalizedType);
        entry.setOperateTime(LocalDateTime.now(SHANGHAI_ZONE));
        entry.setSuccess(1);
        entry.setTarget(truncateTarget(normalizedTarget));
        entry.setIp(clientIp);
        entry.setSystemCode(system.getSystemCode());
        entry.setDetail(serializeDetail(
                normalizedObjectType, normalizedObjectId, normalizedTarget, termId, courseId, classId));
        entry.setBeforeSnapshot(toJsonOrNull(before));
        entry.setAfterSnapshot(toJsonOrNull(after));
        entry.setDecisionId(EXT_AUDIT_DECISION_ID);
        entry.setRequestId(normalizedRequestId);
        IamLog saved = logRepository.save(entry);
        return toAuditLogData(saved,
                resolveAuditActor(saved.getUserId(), saved.getUsername(), actorCache).actor());
    }

    private void requireAuditQueryAccess(Long operatorId, IamSystem system) {
        boolean hasAuditPermission = AUDIT_QUERY_PERMISSIONS.stream()
                .anyMatch(code -> hasPermissionCode(system, operatorId, code));
        if (hasAuditPermission) {
            return;
        }
        boolean hasAllowedRole = externalRoles(operatorId).stream()
                .anyMatch(AUDIT_QUERY_ROLES::contains);
        if (!hasAllowedRole) {
            throw new BusinessException(ErrorCode.NO_FUNCTION_PERM, "你没有查看课程日志的权限。");
        }
    }

    private boolean hasPermissionCode(IamSystem system, Long userId, String permissionCode) {
        return permissionRepository.findBySystemIdAndPermissionCode(system.getId(), permissionCode)
                .filter(permission -> permission.getIsDeleted() == 0)
                .filter(permission -> IamPermission.STATUS_ACTIVE.equals(permission.getStatus()))
                .map(permission -> decisionService.hasPermission(
                        userId, system.getSystemCode(), permission.getId()))
                .orElse(false);
    }

    private List<String> externalRoles(Long userId) {
        return decisionService.getUserRoleCodes(userId).stream()
                .map(this::toExternalRole)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private Specification<IamLog> buildExternalAuditSpecification(
            String systemCode, Long actorId, LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("systemCode"), systemCode));
            predicates.add(cb.equal(root.get("decisionId"), EXT_AUDIT_DECISION_ID));
            if (actorId != null) {
                predicates.add(cb.equal(root.get("userId"), actorId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("operateTime"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("operateTime"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private ResolvedAuditActor resolveAuditActor(
            Long actorUserId, String fallbackUsername, Map<Long, ResolvedAuditActor> cache) {
        if (actorUserId == null) {
            return new ResolvedAuditActor(
                    new IdentityAuditActorData(null, fallbackUsername, List.of(), true),
                    fallbackUsername);
        }
        return cache.computeIfAbsent(actorUserId, id -> userRepository.findById(id)
                .map(user -> {
                    boolean deleted = Integer.valueOf(1).equals(user.getIsDeleted());
                    String name = decryptNameOrFallback(user.getNameEnc(), user.getUserNo(), fallbackUsername);
                    List<String> roles = deleted
                            || !AccountStatus.NORMAL.getCode().equals(user.getAccountStatus())
                            ? List.of()
                            : externalRoles(id);
                    return new ResolvedAuditActor(
                            new IdentityAuditActorData(id, name, roles, deleted),
                            user.getUserNo());
                })
                .orElseGet(() -> new ResolvedAuditActor(
                        new IdentityAuditActorData(id, fallbackUsername, List.of(), true),
                        fallbackUsername)));
    }

    private IdentityAuditLogData toAuditLogData(IamLog entry, IdentityAuditActorData actor) {
        Map<String, Object> detail = parseJsonMap(entry.getDetail());
        String fullTarget = stringValue(detail.get("target"));
        String resolvedTarget = fullTarget != null && !fullTarget.isBlank()
                ? fullTarget
                : entry.getTarget();
        return new IdentityAuditLogData(
                entry.getLogId(),
                entry.getOperateTime() == null
                        ? null
                        : entry.getOperateTime().atZone(SHANGHAI_ZONE).toOffsetDateTime(),
                actor,
                entry.getAction(),
                resolvedTarget,
                parseJsonMap(entry.getBeforeSnapshot()),
                parseJsonMap(entry.getAfterSnapshot()),
                stringValue(detail.get("termId")),
                stringValue(detail.get("courseId")),
                stringValue(detail.get("classId")));
    }

    private String decryptNameOrFallback(String nameEnc, String userNo, String fallbackUsername) {
        if (nameEnc != null && !nameEnc.isBlank()) {
            return cryptoComponent.decrypt(nameEnc);
        }
        return userNo != null && !userNo.isBlank() ? userNo : fallbackUsername;
    }

    private boolean matchesActor(ResolvedAuditActor resolvedActor, String logUsername, String keyword) {
        return containsIgnoreCase(resolvedActor.actor().name(), keyword)
                || containsIgnoreCase(resolvedActor.userNo(), keyword)
                || containsIgnoreCase(logUsername, keyword);
    }

    private boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private String normalizeKeyword(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, message);
        }
        return value.trim();
    }

    private LocalDateTime toShanghaiLocalDateTime(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(SHANGHAI_ZONE).toLocalDateTime();
    }

    private String truncateTarget(String target) {
        return target.length() <= MAX_LOG_TARGET_LENGTH
                ? target
                : target.substring(0, MAX_LOG_TARGET_LENGTH);
    }

    private String serializeDetail(
            String objectType, String objectId, String target,
            String termId, String courseId, String classId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("objectType", objectType);
        detail.put("objectId", objectId);
        detail.put("target", target);
        detail.put("termId", termId);
        detail.put("courseId", courseId);
        detail.put("classId", classId);
        return toJsonOrNull(detail);
    }

    private String toJsonOrNull(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "审计日志序列化失败");
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, MAP_TYPE);
            return parsed == null ? Map.of() : parsed;
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record ResolvedAuditActor(IdentityAuditActorData actor, String userNo) {
    }

    private IamOrg requireDepartment(Long departmentId) {
        return orgRepository.findById(departmentId)
                .filter(department -> department.getIsDeleted() == 0)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "统一身份认证系统中没有找到该部门。")); 
    }

    private boolean hasActiveChildren(Long departmentId) {
        return orgRepository.findByParentId(departmentId).stream()
                .anyMatch(child -> child.getIsDeleted() == 0
                        && IamOrg.STATUS_ACTIVE.equals(child.getStatus()));
    }

    private <S, T> IdentityPageSlice<T> pageOf(
            List<S> source, int page, int pageSize, Function<S, T> mapper) {
        int from = (int) Math.min((long) (page - 1) * pageSize, source.size());
        int to = Math.min(from + pageSize, source.size());
        return new IdentityPageSlice<>(source.size(),
                source.subList(from, to).stream().map(mapper).toList());
    }

    private IamUser requireActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getIsDeleted() == 0)
                .filter(user -> AccountStatus.NORMAL.getCode().equals(user.getAccountStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED, "请先登录"));
    }

    private IamSystem requireSystem(String systemCode) {
        return systemRepository.findBySystemCode(systemCode)
                .filter(system -> system.getIsDeleted() == 0)
                .filter(system -> IamSystem.STATUS_ACTIVE.equals(system.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID, "令牌目标系统无效"));
    }

    private String toExternalRole(String roleCode) {
        String normalized = roleCode.toLowerCase(Locale.ROOT);
        if (normalized.equals("admin") || normalized.contains("admin")) {
            return "admin";
        }
        if (normalized.contains("teacher")) {
            return "teacher";
        }
        if (normalized.contains("student")) {
            return "student";
        }
        return null;
    }
}

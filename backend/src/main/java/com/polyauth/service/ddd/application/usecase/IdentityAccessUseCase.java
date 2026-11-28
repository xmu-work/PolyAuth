package com.polyauth.service.ddd.application.usecase;

import com.polyauth.service.ddd.application.exception.ApplicationErrorCode;
import com.polyauth.service.ddd.application.exception.UseCaseException;
import com.polyauth.service.ddd.application.port.AccountCrypto;
import com.polyauth.service.ddd.application.result.IdentityAuditActor;
import com.polyauth.service.ddd.application.result.IdentityAuditLog;
import com.polyauth.service.ddd.application.result.IdentityDepartment;
import com.polyauth.service.ddd.application.result.IdentityDepartmentUser;
import com.polyauth.service.ddd.application.result.IdentityProfile;
import com.polyauth.service.ddd.application.result.IdentityUser;
import com.polyauth.service.ddd.domain.model.BusinessSystem;
import com.polyauth.service.ddd.domain.model.ExternalAuditRecord;
import com.polyauth.service.ddd.domain.model.Organization;
import com.polyauth.service.ddd.domain.model.PermissionNode;
import com.polyauth.service.ddd.domain.model.UserAccount;
import com.polyauth.service.ddd.domain.repository.ExternalAuditLogRepository;
import com.polyauth.service.ddd.domain.repository.OrganizationRepository;
import com.polyauth.service.ddd.domain.repository.PermissionAdministrationRepository;
import com.polyauth.service.ddd.domain.repository.SystemRepository;
import com.polyauth.service.ddd.domain.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * 统一身份查询用例，实现课程业务系统所需的完整 8 接口契约。
 */
@Service
@Transactional(readOnly = true)
public class IdentityAccessUseCase {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> AUDIT_QUERY_PERMISSIONS = Set.of("AV", "MLG");
    private static final Set<String> AUDIT_QUERY_ROLES = Set.of("admin", "teacher");

    private final UserAccountRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final SystemRepository systemRepository;
    private final PermissionAdministrationRepository permissionRepository;
    private final PermissionAccessUseCase permissionAccessUseCase;
    private final AccountCrypto crypto;
    private final ExternalAuditLogRepository externalAuditLogRepository;

    /**
     * 注入身份目录、权限与外部审计日志查询依赖。
     */
    public IdentityAccessUseCase(
            UserAccountRepository userRepository,
            OrganizationRepository organizationRepository,
            SystemRepository systemRepository,
            PermissionAdministrationRepository permissionRepository,
            PermissionAccessUseCase permissionAccessUseCase,
            AccountCrypto crypto,
            ExternalAuditLogRepository externalAuditLogRepository) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.systemRepository = systemRepository;
        this.permissionRepository = permissionRepository;
        this.permissionAccessUseCase = permissionAccessUseCase;
        this.crypto = crypto;
        this.externalAuditLogRepository = externalAuditLogRepository;
    }

    /**
     * 查询当前用户、角色和前端权限码。
     */
    public IdentityProfile currentUser(Long userId, String systemCode) {
        UserAccount user = requireActive(userId);
        BusinessSystem system = requireSystem(systemCode);
        Set<Long> ids = permissionAccessUseCase.permissions(
                userId, system.systemCode());
        List<String> permissions = permissionRepository.findByIds(ids).stream()
                .filter(item -> item.isActive()
                        && item.systemId().equals(system.id()))
                .map(PermissionNode::permissionCode)
                .sorted()
                .toList();
        List<String> roles = permissionAccessUseCase.roleCodes(userId).stream()
                .map(this::externalRole)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        return new IdentityProfile(user.userNo(), roles, permissions,
                crypto.decrypt(user.nameEnc()), user.userNo());
    }

    /**
     * 校验当前用户是否拥有指定前端权限码。
     */
    public boolean hasPermissionCode(Long userId, String systemCode,
                                     String permissionCode) {
        requireActive(userId);
        BusinessSystem system = requireSystem(systemCode);
        return permissionRepository.findBySystemAndCode(
                        system.id(), permissionCode)
                .filter(PermissionNode::isActive)
                .map(item -> permissionAccessUseCase.hasPermission(
                        userId, system.systemCode(), item.id()))
                .orElse(false);
    }

    /**
     * 查询部门直接下级分页。
     */
    public PageSlice<IdentityDepartment> departments(
            String keyword, Long parentId, int page, int pageSize) {
        List<Organization> source;
        if (parentId == null) {
            source = organizationRepository.findRoots();
        } else {
            requireDepartment(parentId);
            source = organizationRepository.findByParentId(parentId);
        }
        String normalized = keyword == null ? ""
                : keyword.trim().toLowerCase(Locale.ROOT);
        List<IdentityDepartment> filtered = source.stream()
                .filter(item -> !item.isDeleted() && item.isActive())
                .filter(item -> normalized.isEmpty()
                        || item.orgCode().toLowerCase(Locale.ROOT).contains(normalized)
                        || item.orgName().toLowerCase(Locale.ROOT).contains(normalized))
                .sorted(Comparator.comparing(Organization::sortNo)
                        .thenComparing(Organization::id))
                .map(item -> new IdentityDepartment(
                        item.id(), item.orgCode(), item.orgName(), item.parentId(),
                        hasActiveChildren(item.id())))
                .toList();
        return page(filtered, page, pageSize);
    }

    /**
     * 查询部门下用户分页。
     */
    public PageSlice<IdentityDepartmentUser> departmentUsers(
            Long departmentId, String keyword, int page, int pageSize) {
        Organization department = requireDepartment(departmentId);
        String normalized = keyword == null ? ""
                : keyword.trim().toLowerCase(Locale.ROOT);
        List<IdentityDepartmentUser> users = userRepository.findByOrgId(departmentId)
                .stream()
                .filter(item -> !item.deleted())
                .filter(item -> {
                    String name = crypto.decrypt(item.nameEnc());
                    return normalized.isEmpty()
                            || item.userNo().toLowerCase(Locale.ROOT).contains(normalized)
                            || name.toLowerCase(Locale.ROOT).contains(normalized);
                })
                .sorted(Comparator.comparing(UserAccount::userNo))
                .map(item -> new IdentityDepartmentUser(
                        item.userNo(), item.userNo(), crypto.decrypt(item.nameEnc()),
                        department.id(), department.orgName()))
                .toList();
        return page(users, page, pageSize);
    }

    /**
     * 管理员按统一身份用户标识查询最小用户信息。
     */
    public IdentityUser lookupUser(Long operatorId, String systemCode,
                                   String identityUserId) {
        requireActive(operatorId);
        if (!hasPermissionCode(operatorId, systemCode, "TEC")) {
            throw new UseCaseException(
                    ApplicationErrorCode.NO_FUNCTION_PERM,
                    "你没有添加教师的权限。");
        }
        UserAccount user = userRepository.findByUserNo(identityUserId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new UseCaseException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "统一身份认证系统中没有找到该用户。"));
        return new IdentityUser(user.userNo(), user.userNo(),
                crypto.decrypt(user.nameEnc()));
    }

    /**
     * 按操作人、操作对象与时间窗口分页查询外部业务审计日志。
     */
    public PageSlice<IdentityAuditLog> queryAuditLogs(
            Long operatorId,
            String systemCode,
            String actor,
            Long actorId,
            String target,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int pageSize) {
        requireActive(operatorId);
        BusinessSystem system = requireSystem(systemCode);
        requireAuditQueryAccess(operatorId, system);
        if (from != null && to != null && from.isAfter(to)) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID,
                    "日志开始时间不能晚于结束时间");
        }
        LocalDateTime fromTime = toShanghaiLocalDateTime(from);
        LocalDateTime toTime = toShanghaiLocalDateTime(to);
        String normalizedActor = normalizeKeyword(actor);
        String normalizedTarget = normalizeKeyword(target);

        List<ExternalAuditRecord> candidates = externalAuditLogRepository.findMatching(
                system.systemCode(), actorId, fromTime, toTime);
        Map<Long, ResolvedAuditActor> actorCache = new HashMap<>();
        List<IdentityAuditLog> matched = new ArrayList<>();
        for (ExternalAuditRecord record : candidates) {
            ResolvedAuditActor resolvedActor = resolveAuditActor(
                    record.actorUserId(), record.actorUserNo(), actorCache);
            if (normalizedActor != null
                    && !matchesActor(resolvedActor, record.actorUserNo(), normalizedActor)) {
                continue;
            }
            if (normalizedTarget != null && !containsIgnoreCase(record.target(), normalizedTarget)) {
                continue;
            }
            matched.add(toAuditLog(record, resolvedActor.actor()));
        }
        return page(matched, page, pageSize);
    }

    /**
     * 按幂等键写入外部业务审计日志；同一系统内相同 requestId 重试时返回原记录。
     */
    @Transactional
    public IdentityAuditLog writeAuditLog(
            Long userId,
            String systemCode,
            String requestId,
            String type,
            String objectType,
            String objectId,
            String target,
            Map<String, Object> before,
            Map<String, Object> after,
            String clientIp) {
        UserAccount actor = requireActive(userId);
        BusinessSystem system = requireSystem(systemCode);
        String normalizedRequestId = requireNonBlank(requestId, "请求幂等键不能为空");
        String normalizedType = requireNonBlank(type, "审计事件类型不能为空");
        String normalizedObjectType = requireNonBlank(objectType, "审计对象类型不能为空");
        String normalizedObjectId = requireNonBlank(objectId, "审计对象标识不能为空");
        String normalizedTarget = requireNonBlank(target, "审计对象摘要不能为空");

        Optional<ExternalAuditRecord> existing = externalAuditLogRepository.findBySystemAndRequestId(
                system.systemCode(), normalizedRequestId);
        Map<Long, ResolvedAuditActor> actorCache = new HashMap<>();
        if (existing.isPresent()) {
            ExternalAuditRecord record = existing.get();
            return toAuditLog(record,
                    resolveAuditActor(record.actorUserId(), record.actorUserNo(), actorCache).actor());
        }

        String termId = "term".equals(normalizedObjectType) ? normalizedObjectId : null;
        String courseId = "course".equals(normalizedObjectType) ? normalizedObjectId : null;
        String classId = "class".equals(normalizedObjectType) ? normalizedObjectId : null;

        ExternalAuditRecord created = externalAuditLogRepository.save(
                new ExternalAuditRecord(
                        null,
                        system.systemCode(),
                        actor.id(),
                        actor.userNo(),
                        normalizedRequestId,
                        normalizedType,
                        normalizedObjectType,
                        normalizedObjectId,
                        normalizedTarget,
                        before,
                        after,
                        termId,
                        courseId,
                        classId,
                        LocalDateTime.now(SHANGHAI_ZONE)),
                clientIp);
        return toAuditLog(created,
                resolveAuditActor(created.actorUserId(), created.actorUserNo(), actorCache).actor());
    }

    private void requireAuditQueryAccess(Long operatorId, BusinessSystem system) {
        boolean hasAuditPermission = AUDIT_QUERY_PERMISSIONS.stream()
                .anyMatch(code -> hasPermissionCode(system, operatorId, code));
        if (hasAuditPermission) {
            return;
        }
        boolean hasAllowedRole = externalRoles(operatorId).stream()
                .anyMatch(AUDIT_QUERY_ROLES::contains);
        if (!hasAllowedRole) {
            throw new UseCaseException(
                    ApplicationErrorCode.NO_FUNCTION_PERM,
                    "你没有查看课程日志的权限。");
        }
    }

    private boolean hasPermissionCode(BusinessSystem system, Long userId, String permissionCode) {
        return permissionRepository.findBySystemAndCode(system.id(), permissionCode)
                .filter(PermissionNode::isActive)
                .map(item -> permissionAccessUseCase.hasPermission(
                        userId, system.systemCode(), item.id()))
                .orElse(false);
    }

    private List<String> externalRoles(Long userId) {
        return permissionAccessUseCase.roleCodes(userId).stream()
                .map(this::externalRole)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private ResolvedAuditActor resolveAuditActor(
            Long actorUserId,
            String fallbackUsername,
            Map<Long, ResolvedAuditActor> cache) {
        if (actorUserId == null) {
            return new ResolvedAuditActor(
                    new IdentityAuditActor(null, fallbackUsername, List.of(), true),
                    fallbackUsername);
        }
        return cache.computeIfAbsent(actorUserId, id -> userRepository.findById(id)
                .map(user -> {
                    boolean deleted = user.deleted();
                    String name = decryptNameOrFallback(user.nameEnc(), user.userNo(), fallbackUsername);
                    List<String> roles = user.canAuthenticate() ? externalRoles(id) : List.of();
                    return new ResolvedAuditActor(
                            new IdentityAuditActor(id, name, roles, deleted),
                            user.userNo());
                })
                .orElseGet(() -> new ResolvedAuditActor(
                        new IdentityAuditActor(id, fallbackUsername, List.of(), true),
                        fallbackUsername)));
    }

    private IdentityAuditLog toAuditLog(ExternalAuditRecord record, IdentityAuditActor actor) {
        return new IdentityAuditLog(
                record.id(),
                record.operateTime() == null
                        ? null
                        : record.operateTime().atZone(SHANGHAI_ZONE).toOffsetDateTime(),
                actor,
                record.type(),
                record.target(),
                record.before(),
                record.after(),
                record.termId(),
                record.courseId(),
                record.classId());
    }

    private String decryptNameOrFallback(String nameEnc, String userNo, String fallbackUsername) {
        if (nameEnc != null && !nameEnc.isBlank()) {
            return crypto.decrypt(nameEnc);
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
            throw new UseCaseException(ApplicationErrorCode.PARAM_INVALID, message);
        }
        return value.trim();
    }

    private LocalDateTime toShanghaiLocalDateTime(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(SHANGHAI_ZONE).toLocalDateTime();
    }

    private record ResolvedAuditActor(IdentityAuditActor actor, String userNo) {
    }

    private UserAccount requireActive(Long userId) {
        return userRepository.findById(userId)
                .filter(UserAccount::canAuthenticate)
                .orElseThrow(() -> new UseCaseException(
                        ApplicationErrorCode.UNAUTHENTICATED, "请先登录"));
    }

    private BusinessSystem requireSystem(String systemCode) {
        return systemRepository.findByCode(systemCode)
                .filter(BusinessSystem::isActive)
                .orElseThrow(() -> new UseCaseException(
                        ApplicationErrorCode.TOKEN_INVALID,
                        "令牌目标系统无效"));
    }

    private Organization requireDepartment(Long departmentId) {
        return organizationRepository.findById(departmentId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new UseCaseException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "统一身份认证系统中没有找到该部门。"));
    }

    private boolean hasActiveChildren(Long departmentId) {
        return organizationRepository.findByParentId(departmentId).stream()
                .anyMatch(child -> !child.isDeleted() && child.isActive());
    }

    private String externalRole(String roleCode) {
        String normalized = roleCode.toLowerCase(Locale.ROOT);
        if (normalized.contains("admin")) {
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

    private <T> PageSlice<T> page(List<T> source, int page, int pageSize) {
        int from = (int) Math.min((long) (page - 1) * pageSize, source.size());
        int to = Math.min(from + pageSize, source.size());
        return new PageSlice<>(source.size(), source.subList(from, to));
    }

    /**
     * 统一身份分页切片。
     */
    public record PageSlice<T>(long total, List<T> items) {

        public PageSlice {
            items = List.copyOf(items);
        }
    }
}

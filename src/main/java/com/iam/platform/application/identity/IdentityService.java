package com.iam.platform.application.identity;

import com.iam.platform.application.decision.DecisionService;
import com.iam.platform.common.enums.AccountStatus;
import com.iam.platform.common.enums.ErrorCode;
import com.iam.platform.common.exception.BusinessException;
import com.iam.platform.infrastructure.crypto.CryptoComponent;
import com.iam.platform.infrastructure.persistence.entity.IamOrg;
import com.iam.platform.infrastructure.persistence.entity.IamPermission;
import com.iam.platform.infrastructure.persistence.entity.IamSystem;
import com.iam.platform.infrastructure.persistence.entity.IamUser;
import com.iam.platform.infrastructure.persistence.entity.IamApi;
import com.iam.platform.infrastructure.persistence.repository.IamApiRepository;
import com.iam.platform.infrastructure.persistence.repository.IamPermissionRepository;
import com.iam.platform.infrastructure.persistence.repository.IamOrgRepository;
import com.iam.platform.infrastructure.persistence.repository.IamSystemRepository;
import com.iam.platform.infrastructure.persistence.repository.IamUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Comparator;
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

    private final IamUserRepository userRepository;
    private final IamOrgRepository orgRepository;
    private final IamApiRepository apiRepository;
    private final IamSystemRepository systemRepository;
    private final IamPermissionRepository permissionRepository;
    private final DecisionService decisionService;
    private final CryptoComponent cryptoComponent;

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

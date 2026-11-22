package com.polyauth.oop.application.usecase;

import com.polyauth.oop.application.exception.ApplicationErrorCode;
import com.polyauth.oop.application.exception.UseCaseException;
import com.polyauth.oop.application.port.AccountCrypto;
import com.polyauth.oop.application.result.IdentityDepartment;
import com.polyauth.oop.application.result.IdentityDepartmentUser;
import com.polyauth.oop.application.result.IdentityProfile;
import com.polyauth.oop.application.result.IdentityUser;
import com.polyauth.oop.domain.model.BusinessSystem;
import com.polyauth.oop.domain.model.Organization;
import com.polyauth.oop.domain.model.PermissionNode;
import com.polyauth.oop.domain.model.UserAccount;
import com.polyauth.oop.domain.repository.OrganizationRepository;
import com.polyauth.oop.domain.repository.PermissionAdministrationRepository;
import com.polyauth.oop.domain.repository.SystemRepository;
import com.polyauth.oop.domain.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * 统一身份查询用例，实现课程业务系统所需的完整 8 接口契约。
 */
@Service
@Transactional(readOnly = true)
public class IdentityAccessUseCase {

    private final UserAccountRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final SystemRepository systemRepository;
    private final PermissionAdministrationRepository permissionRepository;
    private final PermissionAccessUseCase permissionAccessUseCase;
    private final AccountCrypto crypto;

    /**
     * 注入身份目录与权限查询依赖。
     */
    public IdentityAccessUseCase(
            UserAccountRepository userRepository,
            OrganizationRepository organizationRepository,
            SystemRepository systemRepository,
            PermissionAdministrationRepository permissionRepository,
            PermissionAccessUseCase permissionAccessUseCase,
            AccountCrypto crypto) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.systemRepository = systemRepository;
        this.permissionRepository = permissionRepository;
        this.permissionAccessUseCase = permissionAccessUseCase;
        this.crypto = crypto;
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
                .filter(Organization::isActive)
                .filter(item -> normalized.isEmpty()
                        || item.orgCode().toLowerCase(Locale.ROOT).contains(normalized)
                        || item.orgName().toLowerCase(Locale.ROOT).contains(normalized))
                .sorted(Comparator.comparing(Organization::sortNo)
                        .thenComparing(Organization::id))
                .map(item -> new IdentityDepartment(
                        item.id(), item.orgCode(), item.orgName(), item.parentId(),
                        !organizationRepository.findByParentId(item.id()).isEmpty()))
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

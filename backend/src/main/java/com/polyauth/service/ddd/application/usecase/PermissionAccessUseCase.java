package com.polyauth.service.ddd.application.usecase;

import com.polyauth.service.ddd.domain.model.PermissionFact;
import com.polyauth.service.ddd.domain.model.Role;
import com.polyauth.service.ddd.domain.model.RoleGrant;
import com.polyauth.service.ddd.domain.model.RolePermissionGrant;
import com.polyauth.service.ddd.domain.model.UserAccount;
import com.polyauth.service.ddd.domain.repository.PermissionCatalogRepository;
import com.polyauth.service.ddd.domain.repository.RoleGrantRepository;
import com.polyauth.service.ddd.domain.repository.RolePermissionRepository;
import com.polyauth.service.ddd.domain.repository.RoleRepository;
import com.polyauth.service.ddd.domain.repository.UserAccountRepository;
import com.polyauth.service.ddd.domain.service.PermissionResolver;

import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 权限查询用例，编排授权事实读取与纯权限计算。
 */
@Service
@Transactional(readOnly = true)
public class PermissionAccessUseCase {

    private static final long DATA_VIEW_PERMISSION_ID = 10009L;

    private final UserAccountRepository userRepository;
    private final RoleGrantRepository roleGrantRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionCatalogRepository permissionCatalogRepository;
    private final Clock clock;

    /**
     * 注入授权数据端口及可控时钟。
     */
    public PermissionAccessUseCase(UserAccountRepository userRepository,
                                   RoleGrantRepository roleGrantRepository,
                                   RoleRepository roleRepository,
                                   RolePermissionRepository rolePermissionRepository,
                                   PermissionCatalogRepository permissionCatalogRepository,
                                   Clock clock) {
        this.userRepository = userRepository;
        this.roleGrantRepository = roleGrantRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionCatalogRepository = permissionCatalogRepository;
        this.clock = clock;
    }

    /**
     * 查询用户在目标系统内的功能权限集合。
     */
    public Set<Long> permissions(Long userId, String systemCode) {
        UserAccount user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.canAuthenticate()) {
            return Set.of();
        }
        List<RoleGrant> grants = roleGrantRepository.findByUserId(userId);
        if (grants.isEmpty()) {
            return Set.of();
        }
        Map<Long, Role> roles = roleRepository.findAll().stream()
                .collect(Collectors.toMap(
                        Role::id, Function.identity(), (left, right) -> left));
        List<RolePermissionGrant> permissionGrants =
                rolePermissionRepository.findByRoleIds(roles.keySet());
        if (permissionGrants.isEmpty()) {
            return Set.of();
        }
        List<Long> permissionIds = permissionGrants.stream()
                .map(RolePermissionGrant::permissionId)
                .distinct()
                .toList();
        Set<Long> resolved = PermissionResolver.resolve(grants, roles, permissionGrants,
                permissionCatalogRepository.findFacts(permissionIds),
                systemCode, clock.instant());

        // 数据查看权限隐式开放该系统的全部菜单和只读按钮（action=VIEW）。
        boolean hasDataView = permissionGrants.stream()
                .anyMatch(grant -> grant.permissionId().equals(DATA_VIEW_PERMISSION_ID));
        if (!hasDataView) {
            return resolved;
        }
        Set<Long> result = new TreeSet<>(resolved);
        permissionCatalogRepository.findViewFacts(systemCode).stream()
                .filter(PermissionFact::active)
                .map(PermissionFact::permissionId)
                .forEach(result::add);
        return Collections.unmodifiableSet(result);
    }

    /**
     * 判断用户是否拥有目标功能权限。
     */
    public boolean hasPermission(Long userId, String systemCode, Long permissionId) {
        return permissions(userId, systemCode).contains(permissionId);
    }

    /**
     * 查询用户当前有效角色编码，供统一身份契约返回。
     */
    public Set<String> roleCodes(Long userId) {
        UserAccount user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.canAuthenticate()) {
            return Set.of();
        }
        List<RoleGrant> grants = roleGrantRepository.findByUserId(userId);
        Map<Long, Role> roles = roleRepository.findAll().stream()
                .collect(Collectors.toMap(
                        Role::id, Function.identity(), (left, right) -> left));
        Set<Long> roleIds = com.polyauth.service.ddd.domain.service.RoleHierarchy
                .activeRoleIds(grants.stream()
                                .filter(grant -> grant.isEffective(clock.instant()))
                                .map(RoleGrant::roleId)
                                .collect(Collectors.toSet()),
                        roleId -> java.util.Optional.ofNullable(roles.get(roleId)));
        return roleIds.stream()
                .map(roles::get)
                .filter(java.util.Objects::nonNull)
                .map(Role::roleCode)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}

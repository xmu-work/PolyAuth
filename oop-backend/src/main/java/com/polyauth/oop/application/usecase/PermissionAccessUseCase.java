package com.polyauth.oop.application.usecase;

import com.polyauth.oop.domain.model.Role;
import com.polyauth.oop.domain.model.RoleGrant;
import com.polyauth.oop.domain.model.RolePermissionGrant;
import com.polyauth.oop.domain.model.UserAccount;
import com.polyauth.oop.domain.repository.PermissionCatalogRepository;
import com.polyauth.oop.domain.repository.RoleGrantRepository;
import com.polyauth.oop.domain.repository.RolePermissionRepository;
import com.polyauth.oop.domain.repository.RoleRepository;
import com.polyauth.oop.domain.repository.UserAccountRepository;
import com.polyauth.oop.domain.service.PermissionResolver;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        return PermissionResolver.resolve(grants, roles, permissionGrants,
                permissionCatalogRepository.findFacts(permissionIds),
                systemCode, clock.instant());
    }

    /**
     * 判断用户是否拥有目标功能权限。
     */
    public boolean hasPermission(Long userId, String systemCode, Long permissionId) {
        return permissions(userId, systemCode).contains(permissionId);
    }
}

package com.polyauth.service.port.role;

import com.polyauth.infrastructure.persistence.structured.entity.IamRole;
import com.polyauth.infrastructure.persistence.structured.entity.IamRolePermission;
import com.polyauth.infrastructure.persistence.structured.entity.IamUserRole;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 角色、授权、继承与互斥用例契约。
 */
public interface RoleUseCase {

    IamRole createRole(String roleCode, String roleName, String roleType, String description);

    IamRole createRole(String roleCode, String roleName, String roleType, String description,
                       Long parentId);

    IamRole createRole(String roleCode, String roleName, String roleType, String description,
                       List<Long> parentIds);

    IamRole updateRole(Long roleId, String roleName, String description, String status,
                       Long parentId, Long operatorId);

    IamRole updateRole(Long roleId, String roleName, String description, String status,
                       List<Long> parentIds, Long operatorId);

    List<Long> getParentIds(Long roleId);

    Map<Long, List<Long>> getParentIds(Collection<Long> roleIds);

    void bindRoleExclusion(Long roleId, Long excludedRoleId, Long operatorId);

    void unbindRoleExclusion(Long roleId, Long excludedRoleId, Long operatorId);

    List<Long> listRoleExclusions(Long roleId);

    IamRole getRole(Long roleId);

    List<IamRole> listRoles();

    IamUserRole grantRole(Long userId, Long roleId, LocalDateTime expireTime,
                          Long grantBy, String grantReason);

    void grantRoles(Long userId, List<Long> roleIds, LocalDateTime expireTime,
                    Long grantBy, String grantReason);

    void revokeRole(Long userId, Long roleId, Long operatorId);

    void revokeAllRoles(Long userId);

    void revokeAllRoles(Long userId, Long operatorId);

    List<IamUserRole> getUserRoles(Long userId);

    List<IamUserRole> getRoleUsers(Long roleId);

    void bindRolePermissions(Long roleId, List<Long> permissionIds,
                             String allowedAction, Long createdBy);

    void unbindRolePermissions(Long roleId, List<Long> permissionIds, Long operatorId);

    List<IamRolePermission> getRolePermissions(Long roleId);
}

package com.polyauth.structured.application.expiration;

import com.polyauth.structured.application.auth.AuthService;
import com.polyauth.structured.application.log.LogService;
import com.polyauth.structured.infrastructure.persistence.entity.IamRoleDataScope;
import com.polyauth.structured.infrastructure.persistence.entity.IamUserRole;
import com.polyauth.structured.infrastructure.persistence.repository.IamRoleDataScopeRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamUserRoleRepository;
import com.polyauth.structured.interfaces.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 临时授权到期回收服务。
 */
@Service
@RequiredArgsConstructor
public class GrantExpirationService {

    private final IamUserRoleRepository userRoleRepository;
    private final IamRoleDataScopeRepository roleDataScopeRepository;
    private final LogService logService;

    /**
     * 将已到期但状态仍为 ACTIVE 的用户角色和数据范围授权标记为 EXPIRED。
     *
     * @return 本次回收的用户角色数和数据范围数
     */
    public ExpirationResult expireDueGrants() {
        LocalDateTime now = LocalDateTime.now();
        List<IamUserRole> userRoles = userRoleRepository
                .findByStatusAndExpireTimeBefore(IamUserRole.STATUS_ACTIVE, now);
        for (IamUserRole grant : userRoles) {
            Map<String, Object> before = userRoleSnapshot(grant);
            grant.setStatus(IamUserRole.STATUS_EXPIRED);
            grant.setUpdatedAt(now);
            logService.recordPermissionChange(null, AuthService.ADMIN_SYSTEM_CODE,
                    "EXPIRE_ROLE", "user:" + grant.getUserId() + "/role:" + grant.getRoleId(),
                    before, userRoleSnapshot(grant), null, RequestIdFilter.currentRequestId());
        }
        if (!userRoles.isEmpty()) {
            userRoleRepository.saveAll(userRoles);
        }

        List<IamRoleDataScope> dataScopes = roleDataScopeRepository
                .findByStatusAndExpireTimeBefore(IamRoleDataScope.STATUS_ACTIVE, now);
        for (IamRoleDataScope scope : dataScopes) {
            Map<String, Object> before = dataScopeSnapshot(scope);
            scope.setStatus(IamRoleDataScope.STATUS_EXPIRED);
            scope.setUpdatedAt(now);
            logService.recordPermissionChange(null, AuthService.ADMIN_SYSTEM_CODE,
                    "EXPIRE_ROLE_DATA_SCOPE",
                    "role:" + scope.getRoleId() + "/data-scope:" + scope.getId(),
                    before, dataScopeSnapshot(scope), null, RequestIdFilter.currentRequestId());
        }
        if (!dataScopes.isEmpty()) {
            roleDataScopeRepository.saveAll(dataScopes);
        }
        return new ExpirationResult(userRoles.size(), dataScopes.size());
    }

    /**
     * 构造用户角色授权到期快照。
     */
    private Map<String, Object> userRoleSnapshot(IamUserRole grant) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", grant.getId());
        snapshot.put("userId", grant.getUserId());
        snapshot.put("roleId", grant.getRoleId());
        snapshot.put("status", grant.getStatus());
        snapshot.put("expireTime", grant.getExpireTime());
        return snapshot;
    }

    /**
     * 构造数据范围授权到期快照。
     */
    private Map<String, Object> dataScopeSnapshot(IamRoleDataScope scope) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", scope.getId());
        snapshot.put("roleId", scope.getRoleId());
        snapshot.put("scopeType", scope.getScopeType());
        snapshot.put("orgId", scope.getOrgId());
        snapshot.put("projectId", scope.getProjectId());
        snapshot.put("status", scope.getStatus());
        snapshot.put("expireTime", scope.getExpireTime());
        return snapshot;
    }
}

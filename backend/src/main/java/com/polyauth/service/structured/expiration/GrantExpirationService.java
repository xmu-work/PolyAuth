package com.polyauth.service.structured.expiration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.polyauth.service.structured.auth.AuthService;
import com.polyauth.service.structured.log.LogService;
import com.polyauth.infrastructure.persistence.structured.entity.IamUserRole;
import com.polyauth.infrastructure.persistence.structured.repository.IamUserRoleRepository;
import com.polyauth.api.shared.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "structured", matchIfMissing = true)
public class GrantExpirationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrantExpirationService.class);

    private final IamUserRoleRepository userRoleRepository;
    private final LogService logService;

    /**
     * 将已到期但状态仍为 ACTIVE 的用户角色授权标记为 EXPIRED。
     *
     * @return 本次回收的用户角色数
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
            LOGGER.info("expired due user-role grants count={}", userRoles.size());
        }

        return new ExpirationResult(userRoles.size());
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

}

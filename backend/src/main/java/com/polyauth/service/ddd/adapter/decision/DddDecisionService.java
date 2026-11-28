package com.polyauth.service.ddd.adapter.decision;

import com.polyauth.service.ddd.application.usecase.PermissionAccessUseCase;
import com.polyauth.service.port.decision.DecisionUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 权限判定用例的 DDD 实现适配器。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddDecisionService implements DecisionUseCase {

    private final PermissionAccessUseCase useCase;

    @Override
    public boolean hasPermission(Long userId, String systemCode, Long permissionId) {
        return useCase.hasPermission(userId, systemCode, permissionId);
    }

    @Override
    public Set<Long> getUserPermissions(Long userId, String systemCode) {
        return useCase.permissions(userId, systemCode);
    }

    @Override
    public Set<String> getUserRoleCodes(Long userId) {
        return useCase.roleCodes(userId);
    }
}

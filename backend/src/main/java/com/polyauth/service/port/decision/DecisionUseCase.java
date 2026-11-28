package com.polyauth.service.port.decision;

import java.util.Set;

/**
 * 权限判定用例契约（PDP）。
 */
public interface DecisionUseCase {

    boolean hasPermission(Long userId, String systemCode, Long permissionId);

    Set<Long> getUserPermissions(Long userId, String systemCode);

    Set<String> getUserRoleCodes(Long userId);
}

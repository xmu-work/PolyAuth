package com.polyauth.service.port.identity;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 统一身份对外用例契约（8 个统一身份接口 + 审计日志）。
 */
public interface IdentityUseCase {

    IdentityProfile getCurrentUser(Long userId, String systemCode);

    boolean hasPermissionCode(Long userId, String systemCode, String permissionCode);

    IdentityPageSlice<IdentityDepartmentData> pageDepartments(
            String keyword, Long parentId, int page, int pageSize);

    boolean hasApiOperation(Long userId, String systemCode, String operationId);

    IdentityPageSlice<IdentityDepartmentUserData> pageDepartmentUsers(
            Long departmentId, String keyword, int page, int pageSize);

    IdentityUserData lookupUser(Long operatorId, String systemCode, String identityUserId);

    IdentityPageSlice<IdentityAuditLogData> pageAuditLogs(
            Long operatorId, String systemCode, String actor, Long actorId,
            String target, OffsetDateTime from, OffsetDateTime to, int page, int pageSize);

    IdentityAuditLogData writeAuditLog(
            Long userId, String systemCode, String requestId, String type,
            String objectType, String objectId, String target,
            Map<String, Object> before, Map<String, Object> after, String clientIp);
}

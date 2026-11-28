package com.polyauth.service.ddd.adapter.identity;

import com.polyauth.service.ddd.application.result.IdentityAuditActor;
import com.polyauth.service.ddd.application.result.IdentityAuditLog;
import com.polyauth.service.ddd.application.result.IdentityDepartment;
import com.polyauth.service.ddd.application.result.IdentityDepartmentUser;
import com.polyauth.service.ddd.application.result.IdentityUser;
import com.polyauth.service.ddd.application.usecase.IdentityAccessUseCase;
import com.polyauth.service.ddd.application.usecase.OperationAccessUseCase;
import com.polyauth.service.port.identity.IdentityAuditActorData;
import com.polyauth.service.port.identity.IdentityAuditLogData;
import com.polyauth.service.port.identity.IdentityDepartmentData;
import com.polyauth.service.port.identity.IdentityDepartmentUserData;
import com.polyauth.service.port.identity.IdentityPageSlice;
import com.polyauth.service.port.identity.IdentityProfile;
import com.polyauth.service.port.identity.IdentityUseCase;
import com.polyauth.service.port.identity.IdentityUserData;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 统一身份用例的 DDD 实现适配器。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddIdentityService implements IdentityUseCase {

    private final IdentityAccessUseCase identityUseCase;
    private final OperationAccessUseCase operationUseCase;

    @Override
    public IdentityProfile getCurrentUser(Long userId, String systemCode) {
        com.polyauth.service.ddd.application.result.IdentityProfile profile =
                identityUseCase.currentUser(userId, systemCode);
        return new IdentityProfile(profile.subject(), profile.roles(),
                profile.permissions(), profile.name(), profile.no());
    }

    @Override
    public boolean hasPermissionCode(Long userId, String systemCode, String permissionCode) {
        return identityUseCase.hasPermissionCode(userId, systemCode, permissionCode);
    }

    @Override
    public IdentityPageSlice<IdentityDepartmentData> pageDepartments(
            String keyword, Long parentId, int page, int pageSize) {
        IdentityAccessUseCase.PageSlice<IdentityDepartment> slice =
                identityUseCase.departments(keyword, parentId, page, pageSize);
        return new IdentityPageSlice<>(slice.total(), slice.items().stream()
                .map(item -> new IdentityDepartmentData(item.id(), item.code(),
                        item.name(), item.parentId(), item.hasChildren()))
                .toList());
    }

    @Override
    public boolean hasApiOperation(Long userId, String systemCode, String operationId) {
        return operationUseCase.operationAllowed(userId, systemCode, operationId);
    }

    @Override
    public IdentityPageSlice<IdentityDepartmentUserData> pageDepartmentUsers(
            Long departmentId, String keyword, int page, int pageSize) {
        IdentityAccessUseCase.PageSlice<IdentityDepartmentUser> slice =
                identityUseCase.departmentUsers(departmentId, keyword, page, pageSize);
        return new IdentityPageSlice<>(slice.total(), slice.items().stream()
                .map(item -> new IdentityDepartmentUserData(item.userId(), item.no(),
                        item.name(), item.departmentId(), item.departmentName()))
                .toList());
    }

    @Override
    public IdentityUserData lookupUser(Long operatorId, String systemCode, String identityUserId) {
        IdentityUser user = identityUseCase.lookupUser(operatorId, systemCode, identityUserId);
        return new IdentityUserData(user.userId(), user.no(), user.name());
    }

    @Override
    public IdentityPageSlice<IdentityAuditLogData> pageAuditLogs(
            Long operatorId, String systemCode, String actor, Long actorId,
            String target, OffsetDateTime from, OffsetDateTime to, int page, int pageSize) {
        IdentityAccessUseCase.PageSlice<IdentityAuditLog> slice =
                identityUseCase.queryAuditLogs(operatorId, systemCode, actor, actorId,
                        target, from, to, page, pageSize);
        return new IdentityPageSlice<>(slice.total(), slice.items().stream()
                .map(this::toData).toList());
    }

    @Override
    public IdentityAuditLogData writeAuditLog(
            Long userId, String systemCode, String requestId, String type,
            String objectType, String objectId, String target,
            Map<String, Object> before, Map<String, Object> after, String clientIp) {
        return toData(identityUseCase.writeAuditLog(userId, systemCode, requestId, type,
                objectType, objectId, target, before, after, clientIp));
    }

    private IdentityAuditLogData toData(IdentityAuditLog log) {
        IdentityAuditActor actor = log.actor();
        IdentityAuditActorData actorData = actor == null ? null
                : new IdentityAuditActorData(actor.id(), actor.name(),
                        actor.roles(), actor.deleted());
        return new IdentityAuditLogData(log.id(), log.at(), actorData, log.type(),
                log.target(), log.before(), log.after(), log.termId(),
                log.courseId(), log.classId());
    }
}

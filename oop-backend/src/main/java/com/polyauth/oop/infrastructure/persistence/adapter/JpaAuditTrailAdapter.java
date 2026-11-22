package com.polyauth.oop.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polyauth.oop.application.port.AuditTrail;
import com.polyauth.oop.infrastructure.persistence.entity.AuditLogEntity;
import com.polyauth.oop.infrastructure.persistence.repository.AuditLogJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一审计端口的只追加 JPA 适配器。
 */
@Repository
public class JpaAuditTrailAdapter implements AuditTrail {

    private static final String TYPE_ACTION = "ACTION";

    private final AuditLogJpaRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * 注入日志 Repository 与受控 JSON 序列化器。
     */
    public JpaAuditTrailAdapter(AuditLogJpaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLogin(Long userId, String userNo, String systemCode, boolean success,
                            String error, String clientIp, String requestId) {
        record(userId, userNo, systemCode, "LOGIN", userNo, success,
                error, clientIp, requestId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRequest(Long userId, String userNo, String systemCode, String action,
                              String target, String clientIp, boolean success,
                              String error, String requestId) {
        record(userId, userNo, systemCode, action, target, success,
                error, clientIp, requestId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPermissionChange(Long operatorId, String systemCode, String action,
                                       String target, Object before, Object after) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setLogType("PERMISSION_CHANGE");
        entity.setUserId(operatorId);
        entity.setSystemCode(systemCode);
        entity.setAction(action);
        entity.setTarget(target);
        entity.setBeforeSnapshot(toJson(before));
        entity.setAfterSnapshot(toJson(after));
        entity.setSuccess(1);
        entity.setOperateTime(LocalDateTime.now());
        repository.save(entity);
    }

    private void record(Long userId, String userNo, String systemCode, String action,
                        String target, boolean success, String error,
                        String clientIp, String requestId) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setLogType(TYPE_ACTION);
        entity.setUserId(userId);
        entity.setUsername(userNo);
        entity.setSystemCode(systemCode);
        entity.setAction(action);
        entity.setTarget(target);
        entity.setDetail(detail(error));
        entity.setIp(clientIp);
        entity.setSuccess(success ? 1 : 0);
        entity.setRequestId(requestId);
        entity.setOperateTime(LocalDateTime.now());
        repository.save(entity);
    }

    private String detail(String error) {
        if (error == null || error.isBlank()) {
            return null;
        }
        try {
            Map<String, String> detail = new LinkedHashMap<>();
            detail.put("error", error);
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            return "{\"error\":\"login_failed\"}";
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("审计快照序列化失败", exception);
        }
    }
}

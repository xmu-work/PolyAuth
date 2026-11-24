package com.polyauth.oop.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polyauth.oop.domain.model.ExternalAuditRecord;
import com.polyauth.oop.domain.repository.ExternalAuditLogRepository;
import com.polyauth.oop.infrastructure.persistence.entity.AuditLogEntity;
import com.polyauth.oop.infrastructure.persistence.repository.AuditLogJpaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 复用只追加 {@code iam_log} 表的外部统一身份审计日志 JPA 适配器。
 */
@Repository
public class JpaExternalAuditLogRepositoryAdapter implements ExternalAuditLogRepository {

    private static final String LOG_TYPE_ACTION = "ACTION";
    private static final String EXT_AUDIT_DECISION_ID = "EXT_AUDIT";
    private static final int MAX_LOG_TARGET_LENGTH = 255;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AuditLogJpaRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * 注入日志 Repository 与 JSON 序列化器。
     */
    public JpaExternalAuditLogRepositoryAdapter(
            AuditLogJpaRepository repository,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ExternalAuditRecord> findBySystemAndRequestId(String systemCode, String requestId) {
        return repository.findFirstBySystemCodeAndDecisionIdAndRequestId(
                        systemCode, EXT_AUDIT_DECISION_ID, requestId)
                .map(this::toDomain);
    }

    @Override
    public ExternalAuditRecord save(ExternalAuditRecord record, String clientIp) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setLogType(LOG_TYPE_ACTION);
        entity.setUserId(record.actorUserId());
        entity.setUsername(record.actorUserNo());
        entity.setAction(record.type());
        entity.setOperateTime(record.operateTime());
        entity.setSuccess(1);
        entity.setTarget(truncateTarget(record.target()));
        entity.setIp(clientIp);
        entity.setSystemCode(record.systemCode());
        entity.setDetail(serializeDetail(record));
        entity.setBeforeSnapshot(toJsonOrNull(record.before()));
        entity.setAfterSnapshot(toJsonOrNull(record.after()));
        entity.setDecisionId(EXT_AUDIT_DECISION_ID);
        entity.setRequestId(record.requestId());
        return toDomain(repository.save(entity));
    }

    @Override
    public List<ExternalAuditRecord> findMatching(
            String systemCode,
            Long actorUserId,
            LocalDateTime from,
            LocalDateTime to) {
        Specification<AuditLogEntity> spec = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("systemCode"), systemCode));
            predicates.add(builder.equal(root.get("decisionId"), EXT_AUDIT_DECISION_ID));
            if (actorUserId != null) {
                predicates.add(builder.equal(root.get("userId"), actorUserId));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("operateTime"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("operateTime"), to));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(spec, Sort.by(Sort.Direction.DESC, "operateTime", "logId"))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private ExternalAuditRecord toDomain(AuditLogEntity entity) {
        Map<String, Object> detail = parseJsonMap(entity.getDetail());
        String fullTarget = stringValue(detail.get("target"));
        String resolvedTarget = fullTarget != null && !fullTarget.isBlank()
                ? fullTarget
                : entity.getTarget();
        return new ExternalAuditRecord(
                entity.getLogId(),
                entity.getSystemCode(),
                entity.getUserId(),
                entity.getUsername(),
                entity.getRequestId(),
                entity.getAction(),
                stringValue(detail.get("objectType")),
                stringValue(detail.get("objectId")),
                resolvedTarget,
                parseJsonMap(entity.getBeforeSnapshot()),
                parseJsonMap(entity.getAfterSnapshot()),
                stringValue(detail.get("termId")),
                stringValue(detail.get("courseId")),
                stringValue(detail.get("classId")),
                entity.getOperateTime());
    }

    private String truncateTarget(String target) {
        if (target == null) {
            return null;
        }
        return target.length() <= MAX_LOG_TARGET_LENGTH
                ? target
                : target.substring(0, MAX_LOG_TARGET_LENGTH);
    }

    private String serializeDetail(ExternalAuditRecord record) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("objectType", record.objectType());
        detail.put("objectId", record.objectId());
        detail.put("target", record.target());
        detail.put("termId", record.termId());
        detail.put("courseId", record.courseId());
        detail.put("classId", record.classId());
        return toJsonOrNull(detail);
    }

    private String toJsonOrNull(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("审计快照序列化失败", exception);
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, MAP_TYPE);
            return parsed == null ? Map.of() : parsed;
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

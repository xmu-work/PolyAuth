package com.polyauth.oop.infrastructure.persistence.adapter;

import com.polyauth.oop.domain.model.AuditEntry;
import com.polyauth.oop.domain.model.AuditQuery;
import com.polyauth.oop.domain.model.DomainPage;
import com.polyauth.oop.domain.repository.AuditQueryRepository;
import com.polyauth.oop.infrastructure.persistence.entity.AuditLogEntity;
import com.polyauth.oop.infrastructure.persistence.repository.AuditLogJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 审计查询端口的 JPA Specification 适配器。
 */
@Repository
public class JpaAuditQueryRepositoryAdapter implements AuditQueryRepository {

    private final AuditLogJpaRepository repository;

    /**
     * 注入审计日志 JPA Repository。
     */
    public JpaAuditQueryRepositoryAdapter(AuditLogJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public DomainPage<AuditEntry> findPage(AuditQuery query, int page, int pageSize) {
        Page<AuditLogEntity> result = repository.findAll(specification(query),
                PageRequest.of(page - 1, pageSize,
                        Sort.by(Sort.Direction.DESC, "operateTime")));
        return new DomainPage<>(result.getTotalElements(), page, pageSize,
                result.getContent().stream().map(this::toDomain).toList());
    }

    @Override
    public List<AuditEntry> findForExport(AuditQuery query, int maxRows) {
        return repository.findAll(specification(query),
                        PageRequest.of(0, maxRows + 1,
                                Sort.by(Sort.Direction.DESC, "operateTime")))
                .getContent().stream().map(this::toDomain).toList();
    }

    private Specification<AuditLogEntity> specification(AuditQuery query) {
        return (root, criteriaQuery, builder) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates =
                    new java.util.ArrayList<>();
            if (query.userId() != null) {
                predicates.add(builder.equal(root.get("userId"), query.userId()));
            }
            if (query.systemCode() != null) {
                predicates.add(builder.equal(root.get("systemCode"), query.systemCode()));
            }
            if (query.logType() != null) {
                predicates.add(builder.equal(root.get("logType"), query.logType()));
            }
            if (query.username() != null) {
                predicates.add(builder.like(root.get("username"),
                        "%" + query.username() + "%"));
            }
            if (query.action() != null) {
                predicates.add(builder.like(root.get("action"),
                        "%" + query.action() + "%"));
            }
            if (query.success() != null) {
                predicates.add(builder.equal(root.get("success"), query.success()));
            }
            if (query.startTime() != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                        root.get("operateTime"), query.startTime()));
            }
            if (query.endTime() != null) {
                predicates.add(builder.lessThanOrEqualTo(
                        root.get("operateTime"), query.endTime()));
            }
            return builder.and(predicates.toArray(
                    new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private AuditEntry toDomain(AuditLogEntity entity) {
        return new AuditEntry(entity.getLogId(), entity.getLogType(),
                entity.getUserId(), entity.getUsername(), entity.getAction(),
                entity.getOperateTime(), entity.getSuccess(), entity.getTarget(),
                entity.getIp(), entity.getSystemCode(), entity.getDetail(),
                entity.getBeforeSnapshot(), entity.getAfterSnapshot(),
                entity.getDecisionId(), entity.getRequestId());
    }
}

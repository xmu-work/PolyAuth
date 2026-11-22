package com.polyauth.oop.domain.repository;

import com.polyauth.oop.domain.model.AuditEntry;
import com.polyauth.oop.domain.model.AuditQuery;
import com.polyauth.oop.domain.model.DomainPage;

import java.util.List;

/**
 * 审计日志查询仓储端口。
 */
public interface AuditQueryRepository {

    DomainPage<AuditEntry> findPage(AuditQuery query, int page, int pageSize);

    List<AuditEntry> findForExport(AuditQuery query, int maxRows);
}

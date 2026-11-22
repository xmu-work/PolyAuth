package com.polyauth.oop.application.usecase;

import com.polyauth.oop.application.exception.ApplicationErrorCode;
import com.polyauth.oop.application.exception.UseCaseException;
import com.polyauth.oop.application.port.AuditTrail;
import com.polyauth.oop.domain.exception.DomainRuleException;
import com.polyauth.oop.domain.model.Organization;
import com.polyauth.oop.domain.model.OrganizationStatus;
import com.polyauth.oop.domain.model.OrganizationType;
import com.polyauth.oop.domain.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组织树应用用例，负责组织生命周期和节点移动编排。
 */
@Service
@Transactional
public class OrganizationUseCase {

    private static final Comparator<Organization> BY_SORT =
            Comparator.comparing(Organization::sortNo).thenComparing(Organization::id);

    private final OrganizationRepository repository;
    private final AuditTrail auditTrail;

    /**
     * 注入组织仓储和审计端口。
     */
    public OrganizationUseCase(OrganizationRepository repository,
                               AuditTrail auditTrail) {
        this.repository = repository;
        this.auditTrail = auditTrail;
    }

    /**
     * 新建组织节点。
     */
    public Organization create(String orgCode, String orgName, String orgType,
                               Integer orgLevel, Long parentId, Integer sortNo,
                               Long operatorId) {
        if (repository.findByCode(orgCode).isPresent()) {
            throw new UseCaseException(
                    ApplicationErrorCode.RESOURCE_DUPLICATE, "组织编码已存在: " + orgCode);
        }
        OrganizationType type = OrganizationType.fromCode(orgType);
        String ancestors = "0";
        if (parentId != null) {
            Organization parent = require(parentId);
            if (parent.level() >= type.level()) {
                throw new UseCaseException(
                        ApplicationErrorCode.PARAM_INVALID,
                        "上级组织层级必须高于本节点");
            }
            ancestors = parent.selfPath();
        }
        Organization saved = repository.save(Organization.create(
                orgCode, orgName, type, orgLevel, parentId, ancestors,
                sortNo, operatorId, LocalDateTime.now()));
        record(operatorId, "CREATE_ORGANIZATION",
                "organization:" + saved.id(), null, snapshot(saved));
        return saved;
    }

    /**
     * 修改组织，停用时同步停用全部后代。
     */
    public Organization update(Long orgId, String name, Integer sortNo,
                               Long leaderUserId, String status, Long operatorId) {
        Organization current = require(orgId);
        OrganizationStatus target = parseStatus(status);
        Map<String, Object> before = snapshot(current);
        Organization saved = repository.save(current.update(
                name, sortNo, leaderUserId, target, operatorId, LocalDateTime.now()));
        if (target == OrganizationStatus.DISABLED) {
            List<Organization> descendants = descendants(current);
            repository.saveAll(descendants.stream()
                    .map(item -> item.disable(operatorId, LocalDateTime.now()))
                    .toList());
        }
        record(operatorId, "UPDATE_ORGANIZATION",
                "organization:" + orgId, before, snapshot(saved));
        return saved;
    }

    /**
     * 查询组织树或指定根节点的扁平子树。
     */
    @Transactional(readOnly = true)
    public List<Organization> tree(Long rootId) {
        if (rootId == null) {
            return repository.findRoots().stream().sorted(BY_SORT).toList();
        }
        Organization root = require(rootId);
        List<Organization> result = new ArrayList<>();
        result.add(root);
        result.addAll(descendants(root));
        result.sort(Comparator.comparing(Organization::level).thenComparing(BY_SORT));
        return result;
    }

    /**
     * 查询组织详情。
     */
    @Transactional(readOnly = true)
    public Organization get(Long orgId) {
        return require(orgId);
    }

    /**
     * 查询直接下级。
     */
    @Transactional(readOnly = true)
    public List<Organization> children(Long orgId) {
        require(orgId);
        return repository.findByParentId(orgId).stream().sorted(BY_SORT).toList();
    }

    /**
     * 移动组织节点并同步后代祖先路径。
     */
    public Organization move(Long orgId, Long newParentId, Long operatorId) {
        Organization node = require(orgId);
        Organization parent = require(newParentId);
        if (parent.contains(orgId)) {
            throw new UseCaseException(
                    ApplicationErrorCode.ORGANIZATION_INVALID_MOVE,
                    "不能移动到自身或自身的下级组织下");
        }
        String oldPrefix = node.selfPath();
        List<Organization> descendants = descendants(node);
        Organization moved = repository.save(node.reparent(
                parent, operatorId, LocalDateTime.now()));
        String newPrefix = moved.selfPath();
        repository.saveAll(descendants.stream()
                .map(item -> item.withAncestors(
                        newPrefix + item.ancestors().substring(oldPrefix.length()),
                        operatorId, LocalDateTime.now()))
                .toList());
        record(operatorId, "MOVE_ORGANIZATION",
                "organization:" + orgId, snapshot(node), snapshot(moved));
        return moved;
    }

    /**
     * 按 ID 查询未删除组织。
     */
    @Transactional(readOnly = true)
    public Organization require(Long orgId) {
        return repository.findById(orgId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new UseCaseException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND, "组织不存在: " + orgId));
    }

    private List<Organization> descendants(Organization node) {
        String prefix = node.selfPath();
        return repository.findByAncestorsPrefix(prefix).stream()
                .filter(item -> item.ancestors().equals(prefix)
                        || item.ancestors().startsWith(prefix + ","))
                .toList();
    }

    private OrganizationStatus parseStatus(String status) {
        if (status == null) {
            return null;
        }
        try {
            return OrganizationStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID,
                    "组织状态必须为 ACTIVE 或 DISABLED");
        }
    }

    private Map<String, Object> snapshot(Organization organization) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", organization.id());
        result.put("parentId", organization.parentId());
        result.put("ancestors", organization.ancestors());
        result.put("orgCode", organization.orgCode());
        result.put("orgName", organization.orgName());
        result.put("orgType", organization.orgType().name());
        result.put("orgLevel", organization.level());
        result.put("leaderUserId", organization.leaderUserId());
        result.put("sortNo", organization.sortNo());
        result.put("status", organization.status().name());
        return result;
    }

    private void record(Long operatorId, String action, String target,
                        Object before, Object after) {
        auditTrail.recordPermissionChange(
                operatorId, "ADMIN", action, target, before, after);
    }
}

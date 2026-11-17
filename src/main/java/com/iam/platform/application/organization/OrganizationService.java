package com.iam.platform.application.organization;

import com.iam.platform.application.auth.AuthService;
import com.iam.platform.application.log.LogService;
import com.iam.platform.common.enums.ErrorCode;
import com.iam.platform.common.enums.OrgType;
import com.iam.platform.common.exception.BusinessException;
import com.iam.platform.infrastructure.persistence.entity.IamOrg;
import com.iam.platform.infrastructure.persistence.repository.IamOrgRepository;
import com.iam.platform.interfaces.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 组织机构管理应用服务（M03）。
 *
 * <p>多级树（总公司→分公司→部门→项目经理部）、节点移动；组织用于账号归属而不用于数据范围判定。
 * 对应 FR-08～FR-11。
 *
 * <p>祖先路径 ancestors 存"0,根ID,…,父ID"，根节点为"0"；子树查询按祖先路径前缀匹配。
 */
@RequiredArgsConstructor
@Service
public class OrganizationService {

    private static final String STATUS_ACTIVE = IamOrg.STATUS_ACTIVE;
    private static final Comparator<IamOrg> BY_SORT = Comparator.comparing(IamOrg::getSortNo).thenComparing(IamOrg::getId);

    private final IamOrgRepository orgRepository;
    private final LogService logService;

    /**
     * 新建组织节点。
     *
     * <p>流程：校验组织类型与层级一致 → 编码查重 → 校验父节点存在且层级更高 → 计算祖先路径 → 落库。
     */
    @Transactional
    public IamOrg createOrg(String orgCode, String orgName, String orgType, Integer orgLevel,
                            Long parentId, Integer sortNo) {
        OrgType type = OrgType.fromCode(orgType);
        if (orgLevel == null || orgLevel != type.getLevel()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "组织层级与类型不一致：" + orgType + " 应为 " + type.getLevel());
        }
        if (orgRepository.findByOrgCode(orgCode).isPresent()) {
            throw new BusinessException(ErrorCode.RESOURCE_DUPLICATE, "组织编码已存在: " + orgCode);
        }

        String ancestors = "0";
        if (parentId != null) {
            IamOrg parent = getOrg(parentId);
            if (parent.getOrgLevel() >= orgLevel) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "上级组织的层级必须高于本节点");
            }
            ancestors = buildAncestors(parent);
        }

        LocalDateTime now = LocalDateTime.now();
        IamOrg org = new IamOrg();
        org.setParentId(parentId);
        org.setAncestors(ancestors);
        org.setOrgCode(orgCode);
        org.setOrgName(orgName);
        org.setOrgType(type.getCode());
        org.setOrgLevel(orgLevel);
        org.setSortNo(sortNo == null ? 0 : sortNo);
        org.setStatus(STATUS_ACTIVE);
        org.setIsDeleted(0);
        org.setCreatedAt(now);
        org.setUpdatedAt(now);
        return orgRepository.save(org);
    }

    /**
     * 修改组织名称、排序号、负责人，并可停用或启用组织。
     */
    @Transactional
    public IamOrg updateOrg(Long orgId, String orgName, Integer sortNo, Long leaderUserId,
                            String status, Long operatorId) {
        IamOrg org = getOrg(orgId);
        Map<String, Object> before = orgSnapshot(org);
        if (status != null && !STATUS_ACTIVE.equals(status)
                && !IamOrg.STATUS_DISABLED.equals(status)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "组织状态必须为 ACTIVE 或 DISABLED");
        }
        if (sortNo != null && sortNo < 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "组织排序号不能为负数");
        }
        LocalDateTime now = LocalDateTime.now();
        if (orgName != null && !orgName.isBlank()) {
            org.setOrgName(orgName);
        }
        if (sortNo != null) {
            org.setSortNo(sortNo);
        }
        if (leaderUserId != null) {
            org.setLeaderUserId(leaderUserId);
        }
        if (status != null) {
            org.setStatus(status);
            if (IamOrg.STATUS_DISABLED.equals(status)) {
                List<IamOrg> descendants = descendantsOf(org);
                descendants.forEach(child -> {
                    child.setStatus(IamOrg.STATUS_DISABLED);
                    child.setUpdatedAt(now);
                    child.setUpdatedBy(operatorId);
                });
                orgRepository.saveAll(descendants);
            }
        }
        org.setUpdatedAt(now);
        org.setUpdatedBy(operatorId);
        IamOrg saved = orgRepository.save(org);
        logService.recordPermissionChange(operatorId, AuthService.ADMIN_SYSTEM_CODE,
                "UPDATE_ORGANIZATION", "organization:" + orgId,
                before, orgSnapshot(saved), null, RequestIdFilter.currentRequestId());
        return saved;
    }

    /**
     * 查询组织树。
     *
     * <p>不传 rootId 时返回顶层节点（下级经 {@link #getChildren} 逐层加载）；
     * 传 rootId 时返回该节点及其全部下级的扁平列表（按层级、排序号排列）。
     */
    public List<IamOrg> getOrgTree(Long rootId) {
        if (rootId == null) {
            return orgRepository.findByParentIdIsNull().stream()
                    .filter(o -> o.getIsDeleted() == 0)
                    .sorted(BY_SORT)
                    .toList();
        }
        IamOrg root = getOrg(rootId);
        List<IamOrg> subtree = new ArrayList<>();
        subtree.add(root);
        subtree.addAll(descendantsOf(root));
        subtree.sort(Comparator.comparing(IamOrg::getOrgLevel).thenComparing(BY_SORT));
        return subtree;
    }

    /**
     * 查询组织详情，不存在或已删除时抛出 RESOURCE_NOT_FOUND。
     */
    public IamOrg getOrg(Long orgId) {
        return orgRepository.findById(orgId)
                .filter(o -> o.getIsDeleted() == 0)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "组织不存在: " + orgId));
    }

    /**
     * 移动组织节点（校验成环/跨级）。
     *
     * <p>流程：校验节点与新上级存在 → 新上级不能是自己或自己的下级（成环）→ 新上级层级必须更高（跨级）
     * → 更新本节点的上级与祖先路径 → 同步替换全部下级祖先路径的前缀。
     */
    @Transactional
    public IamOrg moveOrg(Long orgId, Long newParentId) {
        IamOrg node = getOrg(orgId);
        IamOrg newParent = getOrg(newParentId);
        if (orgId.equals(newParentId) || isDescendant(newParent, orgId)) {
            throw new BusinessException(ErrorCode.ORG_MOVE_INVALID, "不能移动到自身或自身的下级组织下");
        }
        if (newParent.getOrgLevel() >= node.getOrgLevel()) {
            throw new BusinessException(ErrorCode.ORG_MOVE_INVALID, "新上级组织的层级必须高于本节点");
        }

        String oldPrefix = buildAncestors(node);
        List<IamOrg> descendants = descendantsOf(node);
        LocalDateTime now = LocalDateTime.now();
        node.setParentId(newParentId);
        node.setAncestors(buildAncestors(newParent));
        node.setUpdatedAt(now);
        String newPrefix = buildAncestors(node);
        for (IamOrg d : descendants) {
            d.setAncestors(newPrefix + d.getAncestors().substring(oldPrefix.length()));
            d.setUpdatedAt(now);
        }
        orgRepository.saveAll(descendants);
        return orgRepository.save(node);
    }

    /**
     * 查询子节点（直接下级）。
     */
    public List<IamOrg> getChildren(Long parentId) {
        getOrg(parentId);
        return orgRepository.findByParentId(parentId).stream()
                .filter(o -> o.getIsDeleted() == 0)
                .sorted(BY_SORT)
                .toList();
    }

    /** 子节点的祖先路径 = 父节点的祖先路径 + 父节点 ID */
    private String buildAncestors(IamOrg parent) {
        return parent.getAncestors() + "," + parent.getId();
    }

    /** 下级节点：祖先路径等于本节点路径，或以"本节点路径,"开头（避免 0,1 误匹配 0,12） */
    private List<IamOrg> descendantsOf(IamOrg node) {
        String prefix = buildAncestors(node);
        return orgRepository.findByAncestorsStartingWith(prefix).stream()
                .filter(o -> o.getIsDeleted() == 0)
                .filter(o -> o.getAncestors().equals(prefix) || o.getAncestors().startsWith(prefix + ","))
                .toList();
    }

    /** node 是否位于 ancestorId 的子树中 */
    private boolean isDescendant(IamOrg node, Long ancestorId) {
        return ("," + node.getAncestors() + ",").contains("," + ancestorId + ",");
    }

    /**
     * 构造组织节点审计快照，保留移动前路径和历史归属。
     */
    private Map<String, Object> orgSnapshot(IamOrg org) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", org.getId());
        snapshot.put("parentId", org.getParentId());
        snapshot.put("ancestors", org.getAncestors());
        snapshot.put("orgCode", org.getOrgCode());
        snapshot.put("orgName", org.getOrgName());
        snapshot.put("orgType", org.getOrgType());
        snapshot.put("orgLevel", org.getOrgLevel());
        snapshot.put("sortNo", org.getSortNo());
        snapshot.put("leaderUserId", org.getLeaderUserId());
        snapshot.put("status", org.getStatus());
        return snapshot;
    }
}

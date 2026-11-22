package com.polyauth.structured.application.datascope;

import com.polyauth.structured.application.auth.AuthService;
import com.polyauth.structured.application.log.LogService;
import com.polyauth.structured.common.enums.ErrorCode;
import com.polyauth.structured.common.exception.BusinessException;
import com.polyauth.structured.infrastructure.persistence.entity.IamOrg;
import com.polyauth.structured.infrastructure.persistence.entity.IamDataScope;
import com.polyauth.structured.infrastructure.persistence.entity.IamRole;
import com.polyauth.structured.infrastructure.persistence.entity.IamRoleDataScope;
import com.polyauth.structured.infrastructure.persistence.repository.IamDataScopeRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamOrgRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamRoleDataScopeRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamRoleRepository;
import com.polyauth.structured.infrastructure.cache.PermissionCacheComponent;
import com.polyauth.structured.infrastructure.cache.BusinessLockExecutor;
import com.polyauth.structured.interfaces.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 数据范围管理服务。
 *
 * <p>负责数据范围字典、角色数据范围授权及临时授权有效期。资源决策由
 * {@link DataPermissionService} 负责。
 */
@Service
@RequiredArgsConstructor
public class DataScopeService {

    private final IamDataScopeRepository dataScopeRepository;
    private final IamRoleDataScopeRepository roleDataScopeRepository;
    private final IamRoleRepository roleRepository;
    private final IamOrgRepository orgRepository;
    private final LogService logService;
    private final PermissionCacheComponent permissionCache;
    private final BusinessLockExecutor lockExecutor;

    /**
     * 创建数据范围字典。
     */
    public IamDataScope createDataScope(String scopeCode, String scopeName, String description, Integer sortNo) {
        return createDataScope(scopeCode, scopeName, description, sortNo, null);
    }

    /**
     * 创建数据范围字典并记录操作人。
     */
    public IamDataScope createDataScope(String scopeCode, String scopeName, String description,
                                        Integer sortNo, Long operatorId) {
        String normalizedCode = scopeCode.trim().toUpperCase();
        if (dataScopeRepository.findByScopeCodeAndIsDeleted(normalizedCode, 0).isPresent()) {
            throw new BusinessException(ErrorCode.RESOURCE_DUPLICATE, "数据范围编码已存在: " + normalizedCode);
        }
        LocalDateTime now = LocalDateTime.now();
        IamDataScope scope = new IamDataScope();
        scope.setScopeCode(normalizedCode);
        scope.setScopeName(scopeName.trim());
        scope.setDescription(description);
        scope.setSortNo(sortNo == null ? 0 : sortNo);
        scope.setStatus(IamDataScope.STATUS_ACTIVE);
        scope.setIsDeleted(0);
        scope.setCreatedAt(now);
        scope.setUpdatedAt(now);
        IamDataScope saved = dataScopeRepository.save(scope);
        recordChange(operatorId, "CREATE_DATA_SCOPE", "data-scope:" + saved.getScopeCode(),
                null, dataScopeSnapshot(saved));
        return saved;
    }

    /**
     * 查询全部数据范围字典。
     */
    public List<IamDataScope> listDataScopes() {
        return dataScopeRepository.findByIsDeletedOrderBySortNoAscIdAsc(0);
    }

    /**
     * 为角色绑定数据范围。
     */
    public IamRoleDataScope bindRoleDataScope(Long roleId, String scopeType, Long orgId, Long projectId,
                                               LocalDateTime expireTime, Long createdBy) {
        return lockExecutor.execute("role-data-scope:bind:" + roleId,
                () -> bindRoleDataScopeLocked(roleId, scopeType, orgId, projectId, expireTime, createdBy));
    }

    /**
     * 在角色数据范围锁内完成目标校验和保存。
     */
    private IamRoleDataScope bindRoleDataScopeLocked(
            Long roleId, String scopeType, Long orgId, Long projectId,
            LocalDateTime expireTime, Long createdBy) {
        IamRole role = roleRepository.findById(roleId)
                .filter(item -> item.getIsDeleted() == 0)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "角色不存在: " + roleId));
        if (!IamRole.STATUS_ACTIVE.equals(role.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "停用角色不能配置数据范围");
        }

        String normalizedType = scopeType.trim().toUpperCase();
        validateTarget(normalizedType, orgId, projectId);
        LocalDateTime now = LocalDateTime.now();
        if (expireTime != null && !expireTime.isAfter(now)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "数据范围失效时间必须晚于当前时间");
        }

        IamRoleDataScope existing = roleDataScopeRepository.findByRoleId(roleId).stream()
                .filter(item -> normalizedType.equals(item.getScopeType())
                        && Objects.equals(orgId, item.getOrgId())
                        && Objects.equals(projectId, item.getProjectId()))
                .findFirst()
                .orElse(null);
        if (existing != null && IamRoleDataScope.STATUS_ACTIVE.equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.GRANT_DUPLICATE, "同一数据范围已授权");
        }

        IamRoleDataScope binding = existing == null ? new IamRoleDataScope() : existing;
        if (existing == null) {
            binding.setRoleId(roleId);
            binding.setScopeType(normalizedType);
            binding.setOrgId(orgId);
            binding.setProjectId(projectId);
            binding.setCreatedAt(now);
        }
        binding.setEffectiveTime(now);
        binding.setExpireTime(expireTime);
        binding.setStatus(IamRoleDataScope.STATUS_ACTIVE);
        binding.setCreatedBy(createdBy);
        binding.setUpdatedAt(now);
        IamRoleDataScope saved = roleDataScopeRepository.save(binding);
        recordChange(createdBy, "BIND_ROLE_DATA_SCOPE", "role:" + roleId + "/data-scope:" + saved.getId(),
                null, dataScopeBindingSnapshot(saved));
        permissionCache.bumpVersion();
        return saved;
    }

    /**
     * 查询角色的数据范围授权。
     */
    public List<IamRoleDataScope> listRoleDataScopes(Long roleId) {
        roleRepository.findById(roleId)
                .filter(item -> item.getIsDeleted() == 0)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "角色不存在: " + roleId));
        return roleDataScopeRepository.findByRoleId(roleId);
    }

    /**
     * 回收角色数据范围授权。
     */
    public void revokeRoleDataScope(Long bindingId, Long operatorId) {
        lockExecutor.execute("role-data-scope:revoke:" + bindingId,
                () -> revokeRoleDataScopeLocked(bindingId, operatorId));
    }

    /**
     * 在授权记录锁内完成回收并保存前后快照。
     */
    private Void revokeRoleDataScopeLocked(Long bindingId, Long operatorId) {
        IamRoleDataScope binding = roleDataScopeRepository.findById(bindingId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "数据范围授权不存在: " + bindingId));
        if (!IamRoleDataScope.STATUS_ACTIVE.equals(binding.getStatus())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "数据范围授权已失效或回收: " + bindingId);
        }
        Map<String, Object> before = dataScopeBindingSnapshot(binding);
        binding.setStatus(IamRoleDataScope.STATUS_REVOKED);
        binding.setUpdatedAt(LocalDateTime.now());
        IamRoleDataScope saved = roleDataScopeRepository.save(binding);
        recordChange(operatorId, "REVOKE_ROLE_DATA_SCOPE",
                "role:" + saved.getRoleId() + "/data-scope:" + saved.getId(),
                before, dataScopeBindingSnapshot(saved));
        permissionCache.bumpVersion();
        return null;
    }

    /**
     * 校验范围类型与目标组织/项目的一致性。
     */
    private void validateTarget(String scopeType, Long orgId, Long projectId) {
        switch (scopeType) {
            case IamRoleDataScope.TYPE_ALL, IamRoleDataScope.TYPE_SELF -> {
                if (orgId != null || projectId != null) {
                    throw invalidTarget("ALL/SELF 范围不能绑定组织或项目");
                }
            }
            case IamRoleDataScope.TYPE_BRANCH -> {
                validateOrganizationTarget(orgId, "BRANCH");
                if (projectId != null) {
                    throw invalidTarget("BRANCH 范围不能绑定项目");
                }
            }
            case IamRoleDataScope.TYPE_DEPT -> {
                validateOrganizationTarget(orgId, "DEPT");
                if (projectId != null) {
                    throw invalidTarget("DEPT 范围不能绑定项目");
                }
            }
            case IamRoleDataScope.TYPE_PROJECT -> {
                if (projectId == null) {
                    throw invalidTarget("PROJECT 范围必须指定项目");
                }
                if (orgId != null) {
                    IamOrg projectOrg = orgRepository.findById(orgId)
                            .filter(item -> item.getIsDeleted() == 0)
                            .orElseThrow(() -> invalidTarget("项目组织不存在: " + orgId));
                    if (!"PROJECT".equals(projectOrg.getOrgType()) || !projectId.equals(projectOrg.getId())) {
                        throw invalidTarget("项目范围的组织与项目 ID 不一致");
                    }
                }
            }
            default -> throw invalidTarget("未知数据范围类型: " + scopeType);
        }
    }

    /**
     * 校验组织型数据范围的节点存在、启用且类型匹配。
     */
    private void validateOrganizationTarget(Long orgId, String expectedType) {
        if (orgId == null) {
            throw invalidTarget(expectedType + " 范围必须指定组织");
        }
        IamOrg org = orgRepository.findById(orgId)
                .filter(item -> item.getIsDeleted() == 0)
                .orElseThrow(() -> invalidTarget("组织不存在: " + orgId));
        if (!expectedType.equals(org.getOrgType())) {
            throw invalidTarget(expectedType + " 范围不能绑定 " + org.getOrgType() + " 组织");
        }
        if (!IamOrg.STATUS_ACTIVE.equals(org.getStatus())) {
            throw invalidTarget("停用组织不能配置数据范围");
        }
    }

    /**
     * 构造稳定的数据范围参数错误。
     */
    private BusinessException invalidTarget(String message) {
        return new BusinessException(ErrorCode.DATA_SCOPE_INVALID, message);
    }

    /**
     * 保存数据范围变更快照供审计追溯。
     */
    private void recordChange(Long operatorId, String action, String target, Object before, Object after) {
        logService.recordPermissionChange(operatorId, AuthService.ADMIN_SYSTEM_CODE, action, target,
                before, after, null, RequestIdFilter.currentRequestId());
    }

    /**
     * 构造数据范围字典快照。
     */
    private Map<String, Object> dataScopeSnapshot(IamDataScope scope) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", scope.getId());
        snapshot.put("scopeCode", scope.getScopeCode());
        snapshot.put("scopeName", scope.getScopeName());
        snapshot.put("description", scope.getDescription());
        snapshot.put("sortNo", scope.getSortNo());
        snapshot.put("status", scope.getStatus());
        return snapshot;
    }

    /**
     * 构造角色数据范围授权快照。
     */
    private Map<String, Object> dataScopeBindingSnapshot(IamRoleDataScope binding) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", binding.getId());
        snapshot.put("roleId", binding.getRoleId());
        snapshot.put("scopeType", binding.getScopeType());
        snapshot.put("orgId", binding.getOrgId());
        snapshot.put("projectId", binding.getProjectId());
        snapshot.put("effectiveTime", binding.getEffectiveTime());
        snapshot.put("expireTime", binding.getExpireTime());
        snapshot.put("status", binding.getStatus());
        snapshot.put("createdBy", binding.getCreatedBy());
        return snapshot;
    }
}

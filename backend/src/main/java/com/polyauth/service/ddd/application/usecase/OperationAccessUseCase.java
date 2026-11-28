package com.polyauth.service.ddd.application.usecase;

import com.polyauth.service.ddd.application.exception.ApplicationErrorCode;
import com.polyauth.service.ddd.application.exception.UseCaseException;
import com.polyauth.service.ddd.domain.repository.ApiPermissionLookup;
import com.polyauth.service.ddd.domain.repository.OperationCatalogRepository;
import org.springframework.stereotype.Service;

/**
 * 管理端 API 默认拒绝判定用例。
 */
@Service
public class OperationAccessUseCase {

    private static final String ADMIN_SYSTEM_CODE = "ADMIN";

    private final OperationCatalogRepository operationCatalogRepository;
    private final PermissionAccessUseCase permissionAccessUseCase;

    /**
     * 注入 API 目录和权限查询用例。
     */
    public OperationAccessUseCase(OperationCatalogRepository operationCatalogRepository,
                                  PermissionAccessUseCase permissionAccessUseCase) {
        this.operationCatalogRepository = operationCatalogRepository;
        this.permissionAccessUseCase = permissionAccessUseCase;
    }

    /**
     * 要求请求已登记、已映射权限且当前用户实际拥有该权限。
     */
    public void requireRequest(Long userId, String apiPath, String httpMethod) {
        ApiPermissionLookup lookup = operationCatalogRepository
                .findByRequest(apiPath, httpMethod)
                .orElseThrow(() -> new UseCaseException(
                        ApplicationErrorCode.NO_FUNCTION_PERM,
                        "接口未登记为 API，默认拒绝"));
        requireMappedPermission(userId, ADMIN_SYSTEM_CODE, lookup);
    }

    /**
     * 按 operationId 返回是否允许调用。
     */
    public boolean operationAllowed(Long userId, String systemCode, String operationId) {
        ApiPermissionLookup lookup = operationCatalogRepository
                .findByOperation(systemCode, operationId)
                .orElseThrow(() -> new UseCaseException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "没有找到对应的 API 操作"));
        return lookup.permissionId() != null
                && permissionAccessUseCase.hasPermission(
                userId, systemCode, lookup.permissionId());
    }

    private void requireMappedPermission(Long userId, String systemCode,
                                         ApiPermissionLookup lookup) {
        if (lookup.permissionId() == null
                || !permissionAccessUseCase.hasPermission(
                userId, systemCode, lookup.permissionId())) {
            throw new UseCaseException(
                    ApplicationErrorCode.NO_FUNCTION_PERM, "无功能权限");
        }
    }
}

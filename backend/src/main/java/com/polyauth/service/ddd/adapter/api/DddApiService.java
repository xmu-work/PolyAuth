package com.polyauth.service.ddd.adapter.api;

import com.polyauth.infrastructure.persistence.structured.entity.IamApi;
import com.polyauth.service.ddd.domain.model.ApiEndpoint;
import com.polyauth.service.ddd.domain.repository.ApiAdministrationRepository;
import com.polyauth.service.port.api.ApiUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 后端 API 目录用例的 DDD 实现适配器。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddApiService implements ApiUseCase {

    private final com.polyauth.service.ddd.application.usecase.ApiCatalogUseCase useCase;
    private final ApiAdministrationRepository apiRepository;

    @Override
    public IamApi registerApi(Long systemId, String apiCode, String apiName,
                              String apiPath, String httpMethod) {
        return toEntity(useCase.register(systemId, apiCode, apiName, apiPath, httpMethod));
    }

    @Override
    public List<IamApi> listApis(Long systemId) {
        return useCase.list(systemId).stream().map(this::toEntity).toList();
    }

    @Override
    public Optional<IamApi> findByRequest(String apiPath, String httpMethod) {
        return apiRepository.findByPathAndMethod(apiPath, httpMethod).map(this::toEntity);
    }

    @Override
    public IamApi getApi(Long apiId) {
        return toEntity(useCase.get(apiId));
    }

    private IamApi toEntity(ApiEndpoint endpoint) {
        IamApi entity = new IamApi();
        entity.setId(endpoint.id());
        entity.setSystemId(endpoint.systemId());
        entity.setPermissionId(endpoint.permissionId());
        entity.setApiCode(endpoint.apiCode());
        entity.setApiName(endpoint.apiName());
        entity.setApiPath(endpoint.apiPath());
        entity.setHttpMethod(endpoint.httpMethod());
        entity.setStatus(endpoint.status());
        entity.setIsDeleted(endpoint.deleted() ? 1 : 0);
        entity.setCreatedAt(endpoint.createdAt());
        entity.setUpdatedAt(endpoint.updatedAt());
        return entity;
    }
}

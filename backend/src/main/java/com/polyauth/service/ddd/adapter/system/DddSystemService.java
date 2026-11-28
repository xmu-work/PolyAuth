package com.polyauth.service.ddd.adapter.system;

import com.polyauth.infrastructure.persistence.structured.entity.IamSystem;
import com.polyauth.service.ddd.domain.model.BusinessSystem;
import com.polyauth.service.port.system.SystemUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 子系统登记用例的 DDD 实现适配器。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddSystemService implements SystemUseCase {

    private final com.polyauth.service.ddd.application.usecase.SystemManagementUseCase useCase;

    @Override
    public IamSystem registerSystem(String systemCode, String systemName, String description) {
        return toEntity(useCase.register(systemCode, systemName, description));
    }

    @Override
    public List<IamSystem> listSystems() {
        return useCase.list().stream().map(this::toEntity).toList();
    }

    @Override
    public IamSystem getSystem(Long systemId) {
        return toEntity(useCase.get(systemId));
    }

    private IamSystem toEntity(BusinessSystem system) {
        IamSystem entity = new IamSystem();
        entity.setId(system.id());
        entity.setSystemCode(system.systemCode());
        entity.setSystemName(system.systemName());
        entity.setCallbackUrl(system.callbackUrl());
        entity.setAppSecret(system.appSecret());
        entity.setStatus(system.status());
        entity.setDescription(system.description());
        entity.setIsDeleted(system.deleted() ? 1 : 0);
        entity.setCreatedAt(system.createdAt());
        entity.setUpdatedAt(system.updatedAt());
        entity.setVersion(system.version());
        return entity;
    }
}

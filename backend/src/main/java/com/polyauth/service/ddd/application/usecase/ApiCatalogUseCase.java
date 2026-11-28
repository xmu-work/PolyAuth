package com.polyauth.service.ddd.application.usecase;

import com.polyauth.service.ddd.application.exception.ApplicationErrorCode;
import com.polyauth.service.ddd.application.exception.UseCaseException;
import com.polyauth.service.ddd.domain.model.ApiEndpoint;
import com.polyauth.service.ddd.domain.repository.ApiAdministrationRepository;
import com.polyauth.service.ddd.domain.repository.SystemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 后端 API 目录登记用例。
 */
@Service
@Transactional
public class ApiCatalogUseCase {

    private final ApiAdministrationRepository apiRepository;
    private final SystemRepository systemRepository;

    /**
     * 注入 API 与系统仓储。
     */
    public ApiCatalogUseCase(ApiAdministrationRepository apiRepository,
                             SystemRepository systemRepository) {
        this.apiRepository = apiRepository;
        this.systemRepository = systemRepository;
    }

    /**
     * 登记 API 元数据。
     */
    public ApiEndpoint register(Long systemId, String code, String name,
                                String path, String method) {
        systemRepository.findById(systemId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new UseCaseException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "系统不存在: " + systemId));
        String normalizedMethod = method.toUpperCase(Locale.ROOT);
        if (apiRepository.findBySystemAndCode(systemId, code).isPresent()) {
            throw new UseCaseException(
                    ApplicationErrorCode.RESOURCE_DUPLICATE,
                    "API 编码已存在: " + code);
        }
        if (apiRepository.findByPathAndMethod(path, normalizedMethod).isPresent()) {
            throw new UseCaseException(
                    ApplicationErrorCode.RESOURCE_DUPLICATE,
                    "API 路径与方法已存在: " + normalizedMethod + " " + path);
        }
        return apiRepository.save(ApiEndpoint.create(
                systemId, code, name, path, normalizedMethod, LocalDateTime.now()));
    }

    /**
     * 查询系统 API 目录。
     */
    @Transactional(readOnly = true)
    public List<ApiEndpoint> list(Long systemId) {
        return apiRepository.findBySystem(systemId);
    }

    /**
     * 查询 API 详情。
     */
    @Transactional(readOnly = true)
    public ApiEndpoint get(Long apiId) {
        return apiRepository.findById(apiId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new UseCaseException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "API 不存在: " + apiId));
    }
}

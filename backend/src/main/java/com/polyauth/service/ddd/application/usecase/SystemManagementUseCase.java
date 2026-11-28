package com.polyauth.service.ddd.application.usecase;

import com.polyauth.service.ddd.application.exception.ApplicationErrorCode;
import com.polyauth.service.ddd.application.exception.UseCaseException;
import com.polyauth.service.ddd.domain.model.BusinessSystem;
import com.polyauth.service.ddd.domain.repository.SystemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 接入系统登记用例。
 */
@Service
@Transactional
public class SystemManagementUseCase {

    private final SystemRepository repository;

    /**
     * 注入业务系统仓储。
     */
    public SystemManagementUseCase(SystemRepository repository) {
        this.repository = repository;
    }

    /**
     * 登记新业务系统。
     */
    public BusinessSystem register(String code, String name, String description) {
        if (repository.findByCode(code).isPresent()) {
            throw new UseCaseException(
                    ApplicationErrorCode.RESOURCE_DUPLICATE, "系统编码已存在: " + code);
        }
        return repository.save(BusinessSystem.create(
                code, name, description, LocalDateTime.now()));
    }

    /**
     * 查询全部未删除系统。
     */
    @Transactional(readOnly = true)
    public List<BusinessSystem> list() {
        return repository.findAll();
    }

    /**
     * 查询系统详情。
     */
    @Transactional(readOnly = true)
    public BusinessSystem get(Long systemId) {
        return repository.findById(systemId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new UseCaseException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "系统不存在: " + systemId));
    }
}

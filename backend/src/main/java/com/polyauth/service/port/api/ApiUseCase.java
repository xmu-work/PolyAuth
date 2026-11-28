package com.polyauth.service.port.api;

import com.polyauth.infrastructure.persistence.structured.entity.IamApi;

import java.util.List;
import java.util.Optional;

/**
 * 后端 API 目录用例契约。
 */
public interface ApiUseCase {

    IamApi registerApi(Long systemId, String apiCode, String apiName,
                       String apiPath, String httpMethod);

    List<IamApi> listApis(Long systemId);

    Optional<IamApi> findByRequest(String apiPath, String httpMethod);

    IamApi getApi(Long apiId);
}

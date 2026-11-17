package com.iam.platform.interfaces.openapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 开放 API 资源联合鉴权请求。
 */
@Data
public class ResourceAuthorizationRequest {

    @NotNull
    @Positive
    private Long userId;

    @NotBlank
    @Size(max = 64)
    private String systemCode;

    @NotNull
    @Positive
    private Long apiId;

    private Long targetOrgId;

    private Long targetProjectId;

    private Long targetUserId;
}

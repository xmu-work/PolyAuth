package com.iam.platform.interfaces.openapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.iam.platform.interfaces.validation.PositiveIds;
import lombok.Data;

import java.util.List;

/**
 * 批量 API 功能权限判定请求。
 */
@Data
public class BatchCheckRequest {

    @NotNull
    @Positive
    private Long userId;

    @NotBlank
    @Size(max = 64)
    private String systemCode;

    @NotEmpty
    @Size(max = 200)
    @PositiveIds
    private List<Long> apiIds;
}

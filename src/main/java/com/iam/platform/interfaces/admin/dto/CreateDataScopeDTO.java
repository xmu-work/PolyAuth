package com.iam.platform.interfaces.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建数据范围字典请求。
 */
@Data
public class CreateDataScopeDTO {

    @NotBlank
    @Size(max = 64)
    private String scopeCode;

    @NotBlank
    @Size(max = 128)
    private String scopeName;

    @Size(max = 500)
    private String description;

    @Min(0)
    @Max(9999)
    private Integer sortNo;
}

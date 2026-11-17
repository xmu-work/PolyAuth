package com.iam.platform.interfaces.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户调岗请求。
 */
@Data
public class TransferUserDTO {

    @NotNull
    private Long orgId;

    @Size(max = 64)
    private String post;
}

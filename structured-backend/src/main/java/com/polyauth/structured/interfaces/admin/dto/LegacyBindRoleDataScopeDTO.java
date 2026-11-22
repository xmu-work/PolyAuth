package com.polyauth.structured.interfaces.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 兼容旧 `/systems/role-data-scopes` 契约的绑定请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LegacyBindRoleDataScopeDTO extends BindRoleDataScopeDTO {

    @NotNull
    private Long roleId;
}

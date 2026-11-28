package com.polyauth.service.ddd.application.command;

import java.util.List;

/**
 * 更新角色用例命令，支持多个父角色。
 */
public record UpdateRoleCommand(Long roleId, String roleName, String description,
                                String status, List<Long> parentIds, Long operatorId) {

    public UpdateRoleCommand {
        parentIds = parentIds == null ? List.of() : List.copyOf(parentIds);
    }

    /**
     * 兼容旧的单父角色调用。
     */
    public UpdateRoleCommand(Long roleId, String roleName, String description,
                             String status, Long parentId, Long operatorId) {
        this(roleId, roleName, description, status,
                parentId == null ? List.of() : List.of(parentId), operatorId);
    }
}

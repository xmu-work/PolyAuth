package com.polyauth.service.ddd.application.command;

import java.util.List;

/**
 * 创建角色用例命令，支持多个父角色。
 */
public record CreateRoleCommand(String roleCode, String roleName, String roleType,
                                String description, List<Long> parentIds, Long operatorId) {

    public CreateRoleCommand {
        parentIds = parentIds == null ? List.of() : List.copyOf(parentIds);
    }

    /**
     * 兼容旧的单父角色调用。
     */
    public CreateRoleCommand(String roleCode, String roleName, String roleType,
                             String description, Long parentId, Long operatorId) {
        this(roleCode, roleName, roleType, description,
                parentId == null ? List.of() : List.of(parentId), operatorId);
    }
}

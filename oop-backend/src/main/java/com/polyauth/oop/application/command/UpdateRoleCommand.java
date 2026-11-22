package com.polyauth.oop.application.command;

/**
 * 更新角色用例命令。
 */
public record UpdateRoleCommand(Long roleId, String roleName, String description,
                                String status, Long parentId, Long operatorId) {
}

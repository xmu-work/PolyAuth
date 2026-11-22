package com.polyauth.oop.application.command;

/**
 * 创建角色用例命令。
 */
public record CreateRoleCommand(String roleCode, String roleName, String roleType,
                                String description, Long parentId, Long operatorId) {
}

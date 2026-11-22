package com.polyauth.oop.domain.model;

import java.util.Set;

/**
 * 权限操作覆盖关系。
 */
public enum OperationAction {
    NONE(Set.of()),
    VIEW(Set.of("VIEW")),
    ALL(Set.of("VIEW", "ADD", "EDIT", "APPROVE", "EXPORT")),
    VIEW_EDIT(Set.of("VIEW", "EDIT")),
    VIEW_ADD(Set.of("VIEW", "ADD")),
    VIEW_APPROVE(Set.of("VIEW", "APPROVE")),
    VIEW_EXPORT(Set.of("VIEW", "EXPORT")),
    VIEW_EDIT_APPROVE(Set.of("VIEW", "EDIT", "APPROVE")),
    VIEW_ADD_EDIT(Set.of("VIEW", "ADD", "EDIT")),
    VIEW_EDIT_EXPORT(Set.of("VIEW", "EDIT", "EXPORT")),
    VIEW_SELF(Set.of("VIEW")),
    VIEW_EDIT_SELF(Set.of("VIEW", "EDIT"));

    private final Set<String> capabilities;

    OperationAction(Set<String> capabilities) {
        this.capabilities = Set.copyOf(capabilities);
    }

    /**
     * 判断当前授权操作是否覆盖目标操作。
     */
    public boolean covers(OperationAction required) {
        return capabilities.containsAll(required.capabilities);
    }
}

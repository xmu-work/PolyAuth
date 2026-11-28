package com.polyauth.infrastructure.persistence.structured.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色多继承边（iam_role_parent）。
 */
@Data
@Entity
@Table(name = "iam_role_parent")
public class IamRoleParent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "parent_id", nullable = false)
    private Long parentId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

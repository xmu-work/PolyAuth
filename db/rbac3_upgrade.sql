-- ============================================================
-- 已部署 RBAC0/RBAC1 数据库升级到 RBAC3
-- MySQL 8.0+；在原有 iam_platform 数据库执行一次。
-- 新安装直接执行 schema.sql + seed.sql，无需执行本文件。
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE iam_role
    DROP CHECK chk_role_type,
    ADD CONSTRAINT chk_role_type CHECK (role_type IN ('FUNC', 'DATA')),
    ADD CONSTRAINT chk_role_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    ADD CONSTRAINT chk_role_parent CHECK (parent_id IS NULL OR parent_id <> id);

CREATE TABLE IF NOT EXISTS iam_data_scope (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    scope_code  VARCHAR(64)  NOT NULL,
    scope_name  VARCHAR(128) NOT NULL,
    description VARCHAR(500) NULL,
    sort_no     INT          NOT NULL DEFAULT 0,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    is_deleted  TINYINT      NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_data_scope_code (scope_code),
    CONSTRAINT chk_data_scope_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据范围字典';

CREATE TABLE IF NOT EXISTS iam_role_data_scope (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    role_id        BIGINT      NOT NULL,
    scope_type     VARCHAR(16) NOT NULL,
    org_id         BIGINT      NULL,
    project_id     BIGINT      NULL,
    effective_time DATETIME    NULL,
    expire_time    DATETIME    NULL,
    status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by     BIGINT      NULL,
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version        BIGINT      NOT NULL DEFAULT 0,
    scope_key      VARCHAR(80) GENERATED ALWAYS AS (
        CONCAT(scope_type, ':', IFNULL(org_id, 0), ':', IFNULL(project_id, 0))
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_data_scope (role_id, scope_key),
    KEY idx_role_data_scope_role_status (role_id, status),
    KEY idx_role_data_scope_org (org_id),
    KEY idx_role_data_scope_project (project_id),
    CONSTRAINT fk_role_data_scope_role FOREIGN KEY (role_id) REFERENCES iam_role (id),
    CONSTRAINT fk_role_data_scope_org FOREIGN KEY (org_id) REFERENCES iam_org (id),
    CONSTRAINT chk_role_data_scope_type CHECK (scope_type IN ('ALL', 'BRANCH', 'DEPT', 'PROJECT', 'SELF')),
    CONSTRAINT chk_role_data_scope_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    CONSTRAINT chk_role_data_scope_period CHECK (
        expire_time IS NULL OR effective_time IS NULL OR expire_time > effective_time
    ),
    CONSTRAINT chk_role_data_scope_target CHECK (
        (scope_type IN ('ALL', 'SELF') AND org_id IS NULL AND project_id IS NULL)
        OR (scope_type IN ('BRANCH', 'DEPT') AND org_id IS NOT NULL AND project_id IS NULL)
        OR (scope_type = 'PROJECT' AND project_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-数据范围授权';

CREATE TABLE IF NOT EXISTS iam_role_exclusion (
    id               BIGINT   NOT NULL AUTO_INCREMENT,
    role_id          BIGINT   NOT NULL,
    excluded_role_id BIGINT   NOT NULL,
    created_by       BIGINT   NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_exclusion (role_id, excluded_role_id),
    KEY idx_role_exclusion_peer (excluded_role_id),
    CONSTRAINT fk_role_exclusion_role FOREIGN KEY (role_id) REFERENCES iam_role (id),
    CONSTRAINT fk_role_exclusion_peer FOREIGN KEY (excluded_role_id) REFERENCES iam_role (id),
    CONSTRAINT chk_role_exclusion_order CHECK (role_id < excluded_role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色静态互斥';

DROP TRIGGER IF EXISTS trg_iam_log_no_update;
DROP TRIGGER IF EXISTS trg_iam_log_no_delete;

DELIMITER $$

CREATE TRIGGER trg_iam_log_no_update
BEFORE UPDATE ON iam_log
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'iam_log is append-only';
END$$

CREATE TRIGGER trg_iam_log_no_delete
BEFORE DELETE ON iam_log
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'iam_log is append-only';
END$$

DELIMITER ;

-- 以下 DML 使用 INSERT IGNORE，可在已部署数据库安全执行。
INSERT IGNORE INTO iam_permission
    (id, system_id, parent_id, permission_code, permission_name, permission_type,
     route_path, action, sort_no, status, is_deleted)
VALUES
(10007, 1, NULL, 'MENU_DATA_SCOPE', '数据权限', 'MENU', '/data-scope', 'VIEW', 7, 'ACTIVE', 0),
(20030, 1, 10007, 'DATA_SCOPE_LIST', '查询数据范围字典', 'BUTTON', NULL, 'VIEW', 1, 'ACTIVE', 0),
(20031, 1, 10007, 'DATA_SCOPE_CREATE', '创建数据范围字典', 'BUTTON', NULL, 'ALL', 2, 'ACTIVE', 0),
(20032, 1, 10007, 'ROLE_DATA_SCOPE_LIST', '查询角色数据范围', 'BUTTON', NULL, 'VIEW', 3, 'ACTIVE', 0),
(20033, 1, 10007, 'ROLE_DATA_SCOPE_BIND', '绑定角色数据范围', 'BUTTON', NULL, 'ALL', 4, 'ACTIVE', 0),
(20034, 1, 10007, 'ROLE_DATA_SCOPE_REVOKE', '回收角色数据范围', 'BUTTON', NULL, 'ALL', 5, 'ACTIVE', 0),
(20035, 1, 10004, 'ROLE_UPDATE', '修改角色与继承', 'BUTTON', NULL, 'ALL', 4, 'ACTIVE', 0),
(20036, 1, 10004, 'ROLE_EXCLUSION_MANAGE', '管理角色互斥', 'BUTTON', NULL, 'ALL', 5, 'ACTIVE', 0),
(20037, 1, 10002, 'ORG_UPDATE', '修改和停用组织', 'BUTTON', NULL, 'ALL', 6, 'ACTIVE', 0),
(20038, 1, 10003, 'USER_TRANSFER', '员工调岗', 'BUTTON', NULL, 'ALL', 5, 'ACTIVE', 0),
(20039, 1, 10003, 'USER_SENSITIVE_VIEW', '查看敏感信息明文', 'BUTTON', NULL, 'ALL', 6, 'ACTIVE', 0);

INSERT IGNORE INTO iam_api
    (id, system_id, permission_id, api_code, api_name, api_path, http_method, status, is_deleted)
VALUES
(30032, 1, 20030, 'DATA_SCOPE_LIST', '查询数据范围字典', '/systems/data-scopes', 'GET', 'ACTIVE', 0),
(30033, 1, 20031, 'DATA_SCOPE_CREATE', '创建数据范围字典', '/systems/data-scopes', 'POST', 'ACTIVE', 0),
(30034, 1, 20032, 'ROLE_DATA_SCOPE_LIST', '查询角色数据范围', '/roles/{roleId}/data-scopes', 'GET', 'ACTIVE', 0),
(30035, 1, 20033, 'ROLE_DATA_SCOPE_BIND', '绑定角色数据范围', '/roles/{roleId}/data-scopes', 'POST', 'ACTIVE', 0),
(30036, 1, 20034, 'ROLE_DATA_SCOPE_REVOKE', '回收角色数据范围', '/roles/{roleId}/data-scopes/{bindingId}', 'DELETE', 'ACTIVE', 0),
(30037, 1, 20035, 'ROLE_UPDATE', '修改角色与继承', '/roles/{roleId}', 'PUT', 'ACTIVE', 0),
(30038, 1, 20036, 'ROLE_EXCLUSION_LIST', '查询角色互斥', '/roles/{roleId}/exclusions', 'GET', 'ACTIVE', 0),
(30039, 1, 20036, 'ROLE_EXCLUSION_BIND', '新建角色互斥', '/roles/{roleId}/exclusions', 'POST', 'ACTIVE', 0),
(30040, 1, 20036, 'ROLE_EXCLUSION_UNBIND', '删除角色互斥', '/roles/{roleId}/exclusions/{excludedRoleId}', 'DELETE', 'ACTIVE', 0),
(30041, 1, 20037, 'ORG_UPDATE', '修改和停用组织', '/organizations/{orgId}', 'PUT', 'ACTIVE', 0),
(30042, 1, 20038, 'USER_TRANSFER', '员工调岗', '/users/{userId}/organization', 'PUT', 'ACTIVE', 0),
(30043, 1, 20031, 'ROLE_DATA_SCOPE_LEGACY', '兼容角色数据范围入口', '/systems/role-data-scopes', 'POST', 'ACTIVE', 0),
(30044, 1, 20029, 'LOG_EXPORT', '导出统一日志', '/logs/export', 'GET', 'ACTIVE', 0),
(30045, 1, 20039, 'USER_SENSITIVE_VIEW', '查看敏感信息明文',
 '/users/{userId}/sensitive', 'GET', 'ACTIVE', 0);

INSERT IGNORE INTO iam_role (id, role_code, role_name, role_type, status, description, is_deleted)
VALUES
(2, 'COMPANY_LEADER', '公司领导', 'FUNC', 'ACTIVE', '跨分公司管理角色基线', 0),
(3, 'DEPT_MANAGER', '部门经理', 'FUNC', 'ACTIVE', '部门人员与权限管理角色基线', 0),
(4, 'PROJECT_MANAGER', '项目经理', 'FUNC', 'ACTIVE', '项目经理部管理角色基线', 0),
(5, 'EMPLOYEE', '普通员工', 'FUNC', 'ACTIVE', '普通业务操作角色基线', 0);

INSERT IGNORE INTO iam_role
    (id, role_code, role_name, role_type, status, description, is_deleted, parent_id)
VALUES
(6, 'DATA_SAFETY', '安全员', 'DATA', 'ACTIVE', '岗位型数据角色基线', 5),
(7, 'DATA_BUDGET', '预算员', 'DATA', 'ACTIVE', '岗位型数据角色基线', 5),
(8, 'DATA_QUALITY', '质检员', 'DATA', 'ACTIVE', '岗位型数据角色基线', 5),
(9, 'DATA_COST', '造价员', 'DATA', 'ACTIVE', '岗位型数据角色基线', 5),
(10, 'DATA_ACCOUNTANT', '会计', 'DATA', 'ACTIVE', '与出纳互斥', 5),
(11, 'DATA_CASHIER', '出纳', 'DATA', 'ACTIVE', '与会计互斥', 5);

INSERT IGNORE INTO iam_role_exclusion (role_id, excluded_role_id, created_by)
VALUES (10, 11, 1);

INSERT IGNORE INTO iam_data_scope
    (id, scope_code, scope_name, description, sort_no, status, is_deleted)
VALUES
(1, 'ALL', '全公司', '可访问接入系统内全部数据', 1, 'ACTIVE', 0),
(2, 'BRANCH', '本分公司', '覆盖授权分公司及其下级节点', 2, 'ACTIVE', 0),
(3, 'DEPT', '本部门', '覆盖授权部门及其下级项目节点', 3, 'ACTIVE', 0),
(4, 'PROJECT', '本项目', '按项目 ID 精确过滤', 4, 'ACTIVE', 0),
(5, 'SELF', '本人', '仅目标用户等于当前用户', 5, 'ACTIVE', 0);

INSERT IGNORE INTO iam_role_permission (role_id, permission_id, allowed_action)
SELECT 1, id, action FROM iam_permission
WHERE id IN (10007, 20030, 20031, 20032, 20033, 20034, 20035, 20036, 20037, 20038, 20039);

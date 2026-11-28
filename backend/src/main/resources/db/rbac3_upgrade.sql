-- ============================================================
-- 已部署 RBAC0/RBAC1 数据库升级到功能权限 RBAC1 + 角色互斥 + 审计
-- MySQL 8.0+；在原有 iam_platform 数据库执行一次。
-- 新安装直接执行 schema.sql + seed.sql，无需执行本文件。
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE iam_role
    DROP CHECK chk_role_type,
    ADD CONSTRAINT chk_role_type CHECK (role_type = 'FUNC'),
    ADD CONSTRAINT chk_role_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    ADD CONSTRAINT chk_role_parent CHECK (parent_id IS NULL OR parent_id <> id);

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
(20035, 1, 10004, 'ROLE_UPDATE', '修改角色与继承', 'BUTTON', NULL, 'ALL', 4, 'ACTIVE', 0),
(20036, 1, 10004, 'ROLE_EXCLUSION_MANAGE', '管理角色互斥', 'BUTTON', NULL, 'ALL', 5, 'ACTIVE', 0),
(20037, 1, 10002, 'ORG_UPDATE', '修改和停用组织', 'BUTTON', NULL, 'ALL', 6, 'ACTIVE', 0),
(20038, 1, 10003, 'USER_TRANSFER', '员工调岗', 'BUTTON', NULL, 'ALL', 5, 'ACTIVE', 0),
(20039, 1, 10003, 'USER_SENSITIVE_VIEW', '查看敏感信息明文', 'BUTTON', NULL, 'ALL', 6, 'ACTIVE', 0);

INSERT IGNORE INTO iam_api
    (id, system_id, permission_id, api_code, api_name, api_path, http_method, status, is_deleted)
VALUES
(30037, 1, 20035, 'ROLE_UPDATE', '修改角色与继承', '/roles/{roleId}', 'PUT', 'ACTIVE', 0),
(30038, 1, 20036, 'ROLE_EXCLUSION_LIST', '查询角色互斥', '/roles/{roleId}/exclusions', 'GET', 'ACTIVE', 0),
(30039, 1, 20036, 'ROLE_EXCLUSION_BIND', '新建角色互斥', '/roles/{roleId}/exclusions', 'POST', 'ACTIVE', 0),
(30040, 1, 20036, 'ROLE_EXCLUSION_UNBIND', '删除角色互斥', '/roles/{roleId}/exclusions/{excludedRoleId}', 'DELETE', 'ACTIVE', 0),
(30041, 1, 20037, 'ORG_UPDATE', '修改和停用组织', '/organizations/{orgId}', 'PUT', 'ACTIVE', 0),
(30042, 1, 20038, 'USER_TRANSFER', '员工调岗', '/users/{userId}/organization', 'PUT', 'ACTIVE', 0),
(30044, 1, 20029, 'LOG_EXPORT', '导出统一日志', '/logs/export', 'GET', 'ACTIVE', 0),
(30045, 1, 20039, 'USER_SENSITIVE_VIEW', '查看敏感信息明文',
 '/users/{userId}/sensitive', 'GET', 'ACTIVE', 0);

INSERT IGNORE INTO iam_role (id, role_code, role_name, role_type, status, description, is_deleted)
VALUES
(2, 'COMPANY_LEADER', '公司领导', 'FUNC', 'ACTIVE', '跨分公司管理角色基线', 0),
(3, 'DEPT_MANAGER', '部门经理', 'FUNC', 'ACTIVE', '部门人员与权限管理角色基线', 0),
(4, 'PROJECT_MANAGER', '项目经理', 'FUNC', 'ACTIVE', '项目经理部管理角色基线', 0),
(5, 'EMPLOYEE', '普通员工', 'FUNC', 'ACTIVE', '普通业务操作角色基线', 0);

INSERT IGNORE INTO iam_role_permission (role_id, permission_id, allowed_action)
SELECT 1, id, action FROM iam_permission
WHERE id IN (20035, 20036, 20037, 20038, 20039);

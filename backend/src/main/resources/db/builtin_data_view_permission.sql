-- 内置“数据查看”权限升级脚本（可重复执行）
-- 1. 登记 DATA_VIEW 权限；2. 自动授予所有角色，且角色权限组件不允许取消。
SET NAMES utf8mb4;

INSERT IGNORE INTO iam_permission
    (id, system_id, parent_id, permission_code, permission_name, permission_type,
     route_path, action, sort_no, status, is_deleted)
VALUES
(10009, 1, NULL, 'DATA_VIEW', '数据查看', 'BUTTON', NULL, 'VIEW', 99, 'ACTIVE', 0);

INSERT IGNORE INTO iam_role_permission (role_id, permission_id, allowed_action)
SELECT id, 10009, 'VIEW' FROM iam_role WHERE is_deleted = 0;

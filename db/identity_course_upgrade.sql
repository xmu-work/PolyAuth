-- ============================================================
-- 课程项目管理系统统一身份接入基线（可重复执行）
-- 适用于已初始化数据库，不修改已有业务数据。
-- ============================================================

SET NAMES utf8mb4;

INSERT INTO iam_system
    (system_code, system_name, status, description, is_deleted)
VALUES
    ('course-project-management', '课程项目管理系统', 'ACTIVE',
     '首个统一身份外部业务系统', 0)
ON DUPLICATE KEY UPDATE
    system_name = VALUES(system_name),
    description = VALUES(description),
    status = 'ACTIVE',
    is_deleted = 0;

INSERT INTO iam_permission
    (system_id, parent_id, permission_code, permission_name,
     permission_type, route_path, action, sort_no, status, is_deleted)
SELECT id, NULL, 'TEC', '添加教师', 'BUTTON', NULL, 'ALL', 1, 'ACTIVE', 0
FROM iam_system
WHERE system_code = 'course-project-management'
ON DUPLICATE KEY UPDATE
    permission_name = VALUES(permission_name),
    permission_type = 'BUTTON',
    action = 'ALL',
    status = 'ACTIVE',
    is_deleted = 0;

INSERT INTO iam_role
    (role_code, role_name, role_type, status, description, is_deleted)
VALUES
    ('admin', '课程管理员', 'FUNC', 'ACTIVE', '课程系统统一身份管理员角色', 0),
    ('teacher', '教师', 'FUNC', 'ACTIVE', '课程系统统一身份教师角色', 0),
    ('student', '学生', 'FUNC', 'ACTIVE', '课程系统统一身份学生角色', 0)
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name),
    role_type = 'FUNC',
    status = 'ACTIVE',
    description = VALUES(description),
    is_deleted = 0;

INSERT INTO iam_role_permission (role_id, permission_id, allowed_action)
SELECT role_item.id, permission_item.id, 'ALL'
FROM iam_role role_item
JOIN iam_permission permission_item
  ON permission_item.permission_code = 'TEC'
JOIN iam_system system_item
  ON system_item.id = permission_item.system_id
 AND system_item.system_code = 'course-project-management'
WHERE role_item.role_code = 'admin'
ON DUPLICATE KEY UPDATE allowed_action = 'ALL';

INSERT INTO iam_user_role
    (user_id, role_id, effective_time, grant_reason, status)
SELECT user_item.id, role_item.id, NOW(), '课程系统联调管理员', 'ACTIVE'
FROM iam_user user_item
JOIN iam_role role_item ON role_item.role_code = 'admin'
WHERE user_item.user_no = 'admin'
ON DUPLICATE KEY UPDATE
    status = 'ACTIVE',
    expire_time = NULL,
    grant_reason = VALUES(grant_reason);

-- 修正旧组织升级脚本遗留在 iam_api 中的 /orgs 路径。
UPDATE iam_api
SET api_path = CASE api_code
        WHEN 'ORG_TREE' THEN '/organizations'
        WHEN 'ORG_CREATE' THEN '/organizations'
        WHEN 'ORG_GET' THEN '/organizations/{orgId}'
        WHEN 'ORG_CHILDREN' THEN '/organizations/{orgId}/children'
        WHEN 'ORG_MOVE' THEN '/organizations/{orgId}/parent'
        ELSE api_path
    END
WHERE api_code IN ('ORG_TREE', 'ORG_CREATE', 'ORG_GET', 'ORG_CHILDREN', 'ORG_MOVE')
  AND api_path LIKE '/orgs%';

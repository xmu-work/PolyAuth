-- 仅保留功能权限：幂等移除数据权限表、数据和接口登记。
-- 适用于已经执行过 RBAC3 数据权限升级脚本的数据库。

SET NAMES utf8mb4;

DROP TABLE IF EXISTS iam_role_data_scope;
DROP TABLE IF EXISTS iam_data_scope;

DELETE role_permission
FROM iam_role_permission role_permission
JOIN iam_permission permission ON permission.id = role_permission.permission_id
WHERE permission.id = 10007
   OR permission.parent_id = 10007
   OR permission.permission_code LIKE 'DATA_SCOPE%'
   OR permission.permission_code LIKE 'ROLE_DATA_SCOPE%';

DELETE FROM iam_api
WHERE api_path LIKE '/systems/%data-scope%'
   OR api_path LIKE '/roles/%/data-scopes%'
   OR api_path LIKE '/open/data-scopes%'
   OR api_path = '/open/authorize/resource'
   OR api_code LIKE 'DATA_SCOPE%'
   OR api_code LIKE 'ROLE_DATA_SCOPE%';

DELETE FROM iam_permission
WHERE id = 10007
   OR parent_id = 10007
   OR permission_code LIKE 'DATA_SCOPE%'
   OR permission_code LIKE 'ROLE_DATA_SCOPE%';

DELETE user_role
FROM iam_user_role user_role
JOIN iam_role role ON role.id = user_role.role_id
WHERE role.role_type = 'DATA';

DELETE role_permission
FROM iam_role_permission role_permission
JOIN iam_role role ON role.id = role_permission.role_id
WHERE role.role_type = 'DATA';

DELETE exclusion
FROM iam_role_exclusion exclusion
JOIN iam_role role
  ON role.id = exclusion.role_id OR role.id = exclusion.excluded_role_id
WHERE role.role_type = 'DATA';

DELETE FROM iam_role WHERE role_type = 'DATA';

DROP PROCEDURE IF EXISTS enforce_function_role_only;

DELIMITER $$

CREATE PROCEDURE enforce_function_role_only()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'iam_role'
          AND constraint_name = 'chk_role_type'
          AND constraint_type = 'CHECK'
    ) THEN
        ALTER TABLE iam_role DROP CHECK chk_role_type;
    END IF;
    ALTER TABLE iam_role
        ADD CONSTRAINT chk_role_type CHECK (role_type = 'FUNC');
END$$

DELIMITER ;

CALL enforce_function_role_only();
DROP PROCEDURE enforce_function_role_only;

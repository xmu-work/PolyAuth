-- ============================================================
-- 课程项目管理系统联调：按组织层级批量授予教师/学生角色（可重复执行）
--
-- 前置条件：
--   1. 已执行 db/schema.sql、db/seed.sql 与 db/identity_course_upgrade.sql
--   2. 已将市政公司 10000 名测试员工及组织架构导入 iam_org / iam_user
--      （保证 iam_user.user_no 与课程库工号/学号一致）
--
-- 授权规则（对齐课程项目管理系统 deploy/README.md 4.4 / 14.5）：
--   - 总公司（HQ）及总公司直属部门的正常在职员工（排除 admin）→ 授予 teacher 角色
--   - 各分公司（BRANCH）及其下级部门/项目部的正常在职员工       → 授予 student 角色
--   - 需要进入课程管理后台的教师，请在执行本脚本后按工号单独授予 admin 角色
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

START TRANSACTION;

SET @course_teacher_role_id = (
    SELECT id FROM iam_role WHERE role_code = 'teacher' AND is_deleted = 0 LIMIT 1
);
SET @course_student_role_id = (
    SELECT id FROM iam_role WHERE role_code = 'student' AND is_deleted = 0 LIMIT 1
);

-- 1. 分公司（BRANCH）及其全部下级组织中的在职员工 → student
INSERT INTO iam_user_role (user_id, role_id, effective_time, grant_reason, status)
SELECT u.id, @course_student_role_id, NOW(), '课程系统联调：分公司员工批量授学生角色', 'ACTIVE'
FROM iam_user u
JOIN iam_org o ON o.id = u.org_id AND o.is_deleted = 0
WHERE @course_student_role_id IS NOT NULL
  AND u.is_deleted = 0
  AND u.account_status = 'NORMAL'
  AND u.user_no <> 'admin'
  AND (
      o.org_type = 'BRANCH'
      OR EXISTS (
          SELECT 1
          FROM iam_org b
          WHERE b.org_type = 'BRANCH'
            AND b.is_deleted = 0
            AND CONCAT(',', o.ancestors, ',') LIKE CONCAT('%,', b.id, ',%')
      )
  )
ON DUPLICATE KEY UPDATE
    status = 'ACTIVE',
    expire_time = NULL,
    grant_reason = VALUES(grant_reason);

-- 2. 总公司（HQ）及总公司直属部门（不属于任何 BRANCH 子树）的在职员工 → teacher
INSERT INTO iam_user_role (user_id, role_id, effective_time, grant_reason, status)
SELECT u.id, @course_teacher_role_id, NOW(), '课程系统联调：总公司员工批量授教师角色', 'ACTIVE'
FROM iam_user u
JOIN iam_org o ON o.id = u.org_id AND o.is_deleted = 0
WHERE @course_teacher_role_id IS NOT NULL
  AND u.is_deleted = 0
  AND u.account_status = 'NORMAL'
  AND u.user_no <> 'admin'
  AND (
      o.org_type = 'HQ'
      OR EXISTS (
          SELECT 1
          FROM iam_org h
          WHERE h.org_type = 'HQ'
            AND h.is_deleted = 0
            AND CONCAT(',', o.ancestors, ',') LIKE CONCAT('%,', h.id, ',%')
      )
  )
  AND o.org_type <> 'BRANCH'
  AND NOT EXISTS (
      SELECT 1
      FROM iam_org b
      WHERE b.org_type = 'BRANCH'
        AND b.is_deleted = 0
        AND CONCAT(',', o.ancestors, ',') LIKE CONCAT('%,', b.id, ',%')
  )
ON DUPLICATE KEY UPDATE
    status = 'ACTIVE',
    expire_time = NULL,
    grant_reason = VALUES(grant_reason);

COMMIT;

-- ============================================================
-- 课程项目管理系统（course-project-management）统一身份接入基线（可重复执行）
-- 适用于已初始化数据库，不修改或覆盖已有管理端业务数据。
-- 涵盖：
--   1. 系统登记：course-project-management
--   2. 课程前端菜单/按钮权限码（MCO~MTEL）、全局 API 权限码（TC~UL）与基础访问权限（COURSE_MEMBER）
--   3. 课程角色：admin / teacher / student 及默认权限绑定
--   4. 课程 OpenAPI 全部 operationId 在 iam_api 中的登记与权限映射
-- ============================================================

SET NAMES utf8mb4;

-- 1. 登记课程项目管理系统
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

SET @cpm_system_id = (
    SELECT id FROM iam_system WHERE system_code = 'course-project-management' LIMIT 1
);

-- 2. 登记课程前端菜单/按钮权限、全局 API 权限及基础访问权限
INSERT INTO iam_permission
    (system_id, parent_id, permission_code, permission_name,
     permission_type, route_path, action, sort_no, status, is_deleted)
VALUES
    -- 课程前端菜单权限
    (@cpm_system_id, NULL, 'MCO',  '课程管理菜单',     'MENU',   '/admin/courses',  'ALL', 1,  'ACTIVE', 0),
    (@cpm_system_id, NULL, 'MTR',  '学期管理菜单',     'MENU',   '/admin/terms',    'ALL', 2,  'ACTIVE', 0),
    (@cpm_system_id, NULL, 'MTE',  '教师管理菜单',     'MENU',   '/admin/teachers', 'ALL', 3,  'ACTIVE', 0),
    (@cpm_system_id, NULL, 'MLG',  '审计日志菜单',     'MENU',   '/admin/logs',     'ALL', 4,  'ACTIVE', 0),
    -- 课程前端按钮权限
    (@cpm_system_id, NULL, 'MCOR', '课程审核按钮',     'BUTTON', NULL, 'ALL', 11, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'MCOE', '课程编辑按钮',     'BUTTON', NULL, 'ALL', 12, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'MCOD', '课程删除按钮',     'BUTTON', NULL, 'ALL', 13, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'MCOT', '课程团队按钮',     'BUTTON', NULL, 'ALL', 14, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'MTRC', '学期新建按钮',     'BUTTON', NULL, 'ALL', 15, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'MTRE', '学期编辑按钮',     'BUTTON', NULL, 'ALL', 16, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'MTRD', '学期删除按钮',     'BUTTON', NULL, 'ALL', 17, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'MTRM', '当前学期按钮',     'BUTTON', NULL, 'ALL', 18, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'MTEC', '教师添加按钮',     'BUTTON', NULL, 'ALL', 19, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'MTEE', '教师编辑按钮',     'BUTTON', NULL, 'ALL', 20, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'MTED', '教师删除按钮',     'BUTTON', NULL, 'ALL', 21, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'MTEL', '统一身份选人按钮', 'BUTTON', NULL, 'ALL', 22, 'ACTIVE', 0),
    -- 课程全局 API 权限
    (@cpm_system_id, NULL, 'TC',   '新建学期',         'BUTTON', NULL, 'ALL', 31, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'TE',   '修改学期',         'BUTTON', NULL, 'ALL', 32, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'TD',   '删除学期',         'BUTTON', NULL, 'ALL', 33, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'TCM',  '维护当前学期',     'BUTTON', NULL, 'ALL', 34, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'CV',   '查询管理端课程',   'BUTTON', NULL, 'ALL', 35, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'CR',   '审核课程',         'BUTTON', NULL, 'ALL', 36, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'CRS',  '重置课程状态',     'BUTTON', NULL, 'ALL', 37, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'CE',   '修改课程',         'BUTTON', NULL, 'ALL', 38, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'CD',   '删除课程',         'BUTTON', NULL, 'ALL', 39, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'CC',   '复制课程',         'BUTTON', NULL, 'ALL', 40, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'CTM',  '维护课程团队',     'BUTTON', NULL, 'ALL', 41, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'TV',   '查询教师',         'BUTTON', NULL, 'ALL', 42, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'TEC',  '添加教师',         'BUTTON', NULL, 'ALL', 43, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'TEE',  '修改教师',         'BUTTON', NULL, 'ALL', 44, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'TED',  '删除教师',         'BUTTON', NULL, 'ALL', 45, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'AV',   '查询课程审计',     'BUTTON', NULL, 'ALL', 46, 'ACTIVE', 0),
    (@cpm_system_id, NULL, 'UL',   '查询统一身份用户', 'BUTTON', NULL, 'ALL', 47, 'ACTIVE', 0),
    -- 课程对象级接口通用基础访问权限（授予所有合法课程角色，具体对象级权限由课程后端 Service 判定）
    (@cpm_system_id, NULL, 'COURSE_MEMBER', '课程系统基础访问', 'BUTTON', NULL, 'VIEW', 99, 'ACTIVE', 0)
ON DUPLICATE KEY UPDATE
    permission_name = VALUES(permission_name),
    permission_type = VALUES(permission_type),
    route_path = VALUES(route_path),
    action = VALUES(action),
    sort_no = VALUES(sort_no),
    status = 'ACTIVE',
    is_deleted = 0;

-- 3. 登记课程角色（admin / teacher / student）
INSERT INTO iam_role
    (role_code, role_name, role_type, status, description, is_deleted)
VALUES
    ('admin',   '课程管理员', 'FUNC', 'ACTIVE', '课程系统统一身份管理员角色', 0),
    ('teacher', '教师',       'FUNC', 'ACTIVE', '课程系统统一身份教师角色', 0),
    ('student', '学生',       'FUNC', 'ACTIVE', '课程系统统一身份学生角色', 0)
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name),
    role_type = 'FUNC',
    status = 'ACTIVE',
    description = VALUES(description),
    is_deleted = 0;

-- 4. 为 admin 授予课程系统全部权限；为 teacher / student 授予基础访问权限 COURSE_MEMBER
INSERT INTO iam_role_permission (role_id, permission_id, allowed_action)
SELECT role_item.id, permission_item.id, permission_item.action
FROM iam_role role_item
JOIN iam_permission permission_item
  ON permission_item.system_id = @cpm_system_id
 AND permission_item.is_deleted = 0
WHERE role_item.role_code = 'admin'
ON DUPLICATE KEY UPDATE allowed_action = VALUES(allowed_action);

INSERT INTO iam_role_permission (role_id, permission_id, allowed_action)
SELECT role_item.id, permission_item.id, permission_item.action
FROM iam_role role_item
JOIN iam_permission permission_item
  ON permission_item.system_id = @cpm_system_id
 AND permission_item.permission_code = 'COURSE_MEMBER'
 AND permission_item.is_deleted = 0
WHERE role_item.role_code IN ('teacher', 'student')
ON DUPLICATE KEY UPDATE allowed_action = VALUES(allowed_action);

-- 5. 登记课程系统 OpenAPI operationId 及权限映射（api_path 加 /cpm 前缀避免与管理端路径冲突）
INSERT INTO iam_api
    (system_id, permission_id, api_code, api_name, api_path, http_method, status, is_deleted)
SELECT @cpm_system_id, p.id, spec.api_code, spec.api_name, spec.api_path, spec.http_method, 'ACTIVE', 0
FROM (
    -- 全局权限控制的课程管理端 API
    SELECT 'createTerm'                 AS api_code, '新建学期'         AS api_name, '/cpm/terms'                             AS api_path, 'POST'   AS http_method, 'TC'            AS perm_code UNION ALL
    SELECT 'updateTerm',                             '修改学期',                     '/cpm/terms/{termId}',                                'PUT',                   'TE'                      UNION ALL
    SELECT 'deleteTerm',                             '删除学期',                     '/cpm/terms/{termId}',                                'DELETE',                'TD'                      UNION ALL
    SELECT 'replaceCurrentTerm',                     '设置当前学期',                 '/cpm/terms/current',                                 'PUT',                   'TCM'                     UNION ALL
    SELECT 'clearCurrentTerm',                       '清除当前学期',                 '/cpm/terms/current',                                 'DELETE',                'TCM'                     UNION ALL
    SELECT 'listAdminCourses',                       '管理端查询课程',               '/cpm/admin/courses',                                 'GET',                   'CV'                      UNION ALL
    SELECT 'reviewCourse',                           '审核课程',                     '/cpm/courses/{courseId}/review',                     'POST',                  'CR'                      UNION ALL
    SELECT 'resetCourseToApplication',               '重置课程为申请态',             '/cpm/courses/{courseId}/reset',                      'POST',                  'CRS'                     UNION ALL
    SELECT 'updateCourse',                           '修改课程',                     '/cpm/courses/{courseId}',                            'PUT',                   'CE'                      UNION ALL
    SELECT 'deleteCourse',                           '删除课程',                     '/cpm/courses/{courseId}',                            'DELETE',                'CD'                      UNION ALL
    SELECT 'setCourseLeaders',                       '设置课程组长',                 '/cpm/courses/{courseId}/leaders',                    'PUT',                   'CTM'                     UNION ALL
    SELECT 'setClassTeachers',                       '设置教学班任课教师',           '/cpm/classes/{classId}/teachers',                    'PUT',                   'CTM'                     UNION ALL
    SELECT 'listTeachers',                           '查询教师列表',                 '/cpm/teachers',                                      'GET',                   'TV'                      UNION ALL
    SELECT 'createTeacher',                          '添加教师',                     '/cpm/teachers',                                      'POST',                  'TEC'                     UNION ALL
    SELECT 'updateTeacher',                          '修改教师',                     '/cpm/teachers/{teacherId}',                          'PUT',                   'TEE'                     UNION ALL
    SELECT 'deleteTeacher',                          '删除教师',                     '/cpm/teachers/{teacherId}',                          'DELETE',                'TED'                     UNION ALL
    SELECT 'listAuditLogs',                          '查询课程审计日志',             '/cpm/audit-logs',                                    'GET',                   'AV'                      UNION ALL
    -- 对象级鉴权的课程业务 API（全局放行给合法课程角色，由课程后端 Service 校验归属/角色）
    SELECT 'courseLogin',                            '课程登录',                     '/cpm/login',                                         'POST',                  'COURSE_MEMBER'           UNION ALL
    SELECT 'getCurrentUser',                         '当前课程用户',                 '/cpm/me',                                            'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'listTerms',                              '查询学期列表',                 '/cpm/terms',                                         'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'createCourse',                           '创建课程',                     '/cpm/courses',                                       'POST',                  'COURSE_MEMBER'           UNION ALL
    SELECT 'submitCourseReview',                     '提交课程审核',                 '/cpm/courses/{courseId}/submit',                     'POST',                  'COURSE_MEMBER'           UNION ALL
    SELECT 'getCourse',                              '查询课程详情',                 '/cpm/courses/{courseId}',                            'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'updateCourseDescription',                '更新课程简介',                 '/cpm/courses/{courseId}/description',                'PATCH',                 'COURSE_MEMBER'           UNION ALL
    SELECT 'copyCourse',                             '复制课程',                     '/cpm/courses/{courseId}/copy',                       'POST',                  'COURSE_MEMBER'           UNION ALL
    SELECT 'listStudentCourses',                     '查询学生课程',                 '/cpm/student/courses',                               'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'listTeacherCourses',                     '查询教师课程',                 '/cpm/teacher/courses',                               'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'listClasses',                            '查询教学班列表',               '/cpm/courses/{courseId}/classes',                    'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'createClasses',                          '创建教学班',                   '/cpm/courses/{courseId}/classes',                    'POST',                  'COURSE_MEMBER'           UNION ALL
    SELECT 'updateClass',                            '修改教学班',                   '/cpm/classes/{classId}',                             'PUT',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'deleteClass',                            '删除教学班',                   '/cpm/classes/{classId}',                             'DELETE',                'COURSE_MEMBER'           UNION ALL
    SELECT 'listClassStudents',                      '查询教学班学生',               '/cpm/classes/{classId}/students',                    'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'addStudent',                             '添加教学班学生',               '/cpm/classes/{classId}/students',                    'POST',                  'COURSE_MEMBER'           UNION ALL
    SELECT 'searchStudentCandidates',                '检索候选学生',                 '/cpm/classes/{classId}/student-candidates',          'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'removeStudent',                          '移除教学班学生',               '/cpm/classes/{classId}/students/{studentId}',        'DELETE',                'COURSE_MEMBER'           UNION ALL
    SELECT 'importRoster',                           '导入学生名册',                 '/cpm/classes/{classId}/roster',                      'POST',                  'COURSE_MEMBER'           UNION ALL
    SELECT 'getCourseGroupRule',                     '查询课程分组规则',             '/cpm/courses/{courseId}/group-rule',                 'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'updateCourseGroupRule',                  '更新课程分组规则',             '/cpm/courses/{courseId}/group-rule',                 'PUT',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'getClassGroupRule',                      '查询教学班分组规则',           '/cpm/classes/{classId}/group-rule',                  'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'setClassGroupRule',                      '设置教学班分组规则',           '/cpm/classes/{classId}/group-rule',                  'PUT',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'deleteClassGroupRule',                   '删除教学班分组规则',           '/cpm/classes/{classId}/group-rule',                  'DELETE',                'COURSE_MEMBER'           UNION ALL
    SELECT 'listCourseGroups',                       '查询课程小组',                 '/cpm/courses/{courseId}/groups',                     'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'createGroupAsStudent',                   '学生创建小组',                 '/cpm/courses/{courseId}/groups',                     'POST',                  'COURSE_MEMBER'           UNION ALL
    SELECT 'listClassGroups',                        '查询教学班小组',               '/cpm/classes/{classId}/groups',                      'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'createGroupAsTeacher',                   '教师创建小组',                 '/cpm/classes/{classId}/groups',                      'POST',                  'COURSE_MEMBER'           UNION ALL
    SELECT 'getGroup',                               '查询小组详情',                 '/cpm/groups/{groupId}',                              'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'updateGroup',                            '修改小组信息',                 '/cpm/groups/{groupId}',                              'PUT',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'dissolveGroup',                          '解散小组',                     '/cpm/groups/{groupId}',                              'DELETE',                'COURSE_MEMBER'           UNION ALL
    SELECT 'listGroupJoinRequests',                  '查询入组申请',                 '/cpm/groups/{groupId}/join-requests',                'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'createGroupJoinRequest',                 '提交入组申请',                 '/cpm/groups/{groupId}/join-requests',                'POST',                  'COURSE_MEMBER'           UNION ALL
    SELECT 'decideGroupJoinRequest',                 '审批入组申请',                 '/cpm/join-requests/{requestId}/decision',            'POST',                  'COURSE_MEMBER'           UNION ALL
    SELECT 'cancelGroupJoinRequest',                 '撤回入组申请',                 '/cpm/join-requests/{requestId}',                     'DELETE',                'COURSE_MEMBER'           UNION ALL
    SELECT 'removeGroupMemberAsLeader',              '组长移除组员',                 '/cpm/groups/{groupId}/members/{studentId}',          'DELETE',                'COURSE_MEMBER'           UNION ALL
    SELECT 'transferGroupLeader',                    '转让组长',                     '/cpm/groups/{groupId}/leader',                       'PUT',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'leaveGroup',                             '退出小组',                     '/cpm/groups/{groupId}/leave',                        'POST',                  'COURSE_MEMBER'           UNION ALL
    SELECT 'listMilestones',                         '查询里程碑列表',               '/cpm/courses/{courseId}/milestones',                 'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'createMilestone',                        '新建里程碑',                   '/cpm/courses/{courseId}/milestones',                 'POST',                  'COURSE_MEMBER'           UNION ALL
    SELECT 'updateMilestone',                        '修改里程碑',                   '/cpm/milestones/{milestoneId}',                      'PUT',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'deleteMilestone',                        '删除里程碑',                   '/cpm/milestones/{milestoneId}',                      'DELETE',                'COURSE_MEMBER'           UNION ALL
    SELECT 'listSubmissions',                        '查询提交列表',                 '/cpm/milestones/{milestoneId}/submissions',          'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'getGroupSubmission',                     '查询小组提交',                 '/cpm/milestones/{milestoneId}/groups/{groupId}/submission', 'GET',            'COURSE_MEMBER'           UNION ALL
    SELECT 'submitMaterials',                        '提交小组材料',                 '/cpm/milestones/{milestoneId}/groups/{groupId}/submission', 'POST',           'COURSE_MEMBER'           UNION ALL
    SELECT 'getMyMilestoneSubmission',               '查询个人里程碑提交',           '/cpm/milestones/{milestoneId}/my-submission',        'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'submitMyMaterials',                      '提交个人材料',                 '/cpm/milestones/{milestoneId}/my-submission',        'POST',                  'COURSE_MEMBER'           UNION ALL
    SELECT 'getSubmissionById',                      '查询提交详情',                 '/cpm/submissions/{submissionId}',                    'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'reviewSubmission',                       '批改提交',                     '/cpm/submissions/{submissionId}/review',             'POST',                  'COURSE_MEMBER'           UNION ALL
    SELECT 'listOralSlots',                          '查询口试时段',                 '/cpm/milestones/{milestoneId}/oral-slots',           'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'createOralSlot',                         '新建口试时段',                 '/cpm/milestones/{milestoneId}/oral-slots',           'POST',                  'COURSE_MEMBER'           UNION ALL
    SELECT 'deleteOralSlot',                         '删除口试时段',                 '/cpm/oral-slots/{slotId}',                           'DELETE',                'COURSE_MEMBER'           UNION ALL
    SELECT 'bookOralSlot',                           '预约口试时段',                 '/cpm/oral-slots/{slotId}/booking',                   'POST',                  'COURSE_MEMBER'           UNION ALL
    SELECT 'cancelBooking',                          '取消口试预约',                 '/cpm/oral-slots/{slotId}/booking',                   'DELETE',                'COURSE_MEMBER'           UNION ALL
    SELECT 'listOralStatus',                         '查询口试状态',                 '/cpm/milestones/{milestoneId}/oral-status',          'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'createGroupOralRecord',                  '录入小组口试记录',             '/cpm/milestones/{milestoneId}/groups/{groupId}/oral-record', 'POST',          'COURSE_MEMBER'           UNION ALL
    SELECT 'createStudentOralRecord',                '录入个人口试记录',             '/cpm/milestones/{milestoneId}/students/{studentId}/oral-record', 'POST',      'COURSE_MEMBER'           UNION ALL
    SELECT 'updateOralRecord',                       '修改口试记录',                 '/cpm/oral-records/{recordId}',                       'PUT',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'summarizeOralMemo',                      '生成口试纪要',                 '/cpm/oral-records/{recordId}/memo',                  'POST',                  'COURSE_MEMBER'           UNION ALL
    SELECT 'getClassGrades',                         '查询教学班成绩',               '/cpm/classes/{classId}/grades',                      'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'exportClassGrades',                      '导出教学班成绩',               '/cpm/classes/{classId}/grades/export',               'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'getMyGrades',                            '查询个人成绩',                 '/cpm/my-grades',                                     'GET',                   'COURSE_MEMBER'           UNION ALL
    SELECT 'downloadFile',                           '下载课程附件',                 '/cpm/files/{fileId}',                                'GET',                   'COURSE_MEMBER'
) AS spec
JOIN iam_permission p
  ON p.system_id = @cpm_system_id
 AND p.permission_code = spec.perm_code
 AND p.is_deleted = 0
ON DUPLICATE KEY UPDATE
    permission_id = VALUES(permission_id),
    api_name = VALUES(api_name),
    status = 'ACTIVE',
    is_deleted = 0;

-- 6. 为内置管理员账号 admin 授予课程管理员角色
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

-- 7. 修正旧组织升级脚本遗留在 iam_api 中的 /orgs 路径
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

-- ============================================================
-- RBAC 初始化数据：多系统、前端权限树、后端 API、管理员
-- 在 schema.sql 之后执行
--
-- 权限 ID 用于前端菜单/按钮控制，API ID 用于后端服务端鉴权，二者不再混用。
-- 默认管理员：admin / Admin@123
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- 1. 管理后台自身作为一个接入系统
INSERT INTO iam_system (id, system_code, system_name, status, description, is_deleted)
VALUES (1, 'ADMIN', '权限管理后台', 'ACTIVE', '本系统管理后台自身', 0);

-- 2. 菜单权限：ID 与 web/src/constants/permissions.ts 保持一致
INSERT INTO iam_permission
    (id, system_id, parent_id, permission_code, permission_name, permission_type, route_path, action, sort_no, status, is_deleted)
VALUES
(10001, 1, NULL, 'MENU_HOME',         '首页',     'MENU', '/home',         'VIEW', 1, 'ACTIVE', 0),
(10002, 1, NULL, 'MENU_ORGANIZATION', '组织机构', 'MENU', '/organization', 'VIEW', 2, 'ACTIVE', 0),
(10003, 1, NULL, 'MENU_USER',         '用户管理', 'MENU', '/user',         'VIEW', 3, 'ACTIVE', 0),
(10004, 1, NULL, 'MENU_ROLE',         '角色管理', 'MENU', '/role',         'VIEW', 4, 'ACTIVE', 0),
(10005, 1, NULL, 'MENU_GRANT',        '授权管理', 'MENU', '/grant',        'VIEW', 5, 'ACTIVE', 0),
(10006, 1, NULL, 'MENU_PERMISSION',   '功能权限', 'MENU', '/permission',   'VIEW', 6, 'ACTIVE', 0),
(10007, 1, NULL, 'MENU_DATA_SCOPE',   '数据权限', 'MENU', '/data-scope',   'VIEW', 7, 'ACTIVE', 0),
(10008, 1, NULL, 'MENU_LOG',          '日志审计', 'MENU', '/logs',         'VIEW', 8, 'ACTIVE', 0);

-- 3. 按钮权限：角色直接授予这些前端权限，API 再挂到按钮权限下
INSERT INTO iam_permission
    (id, system_id, parent_id, permission_code, permission_name, permission_type, route_path, action, sort_no, status, is_deleted)
VALUES
(20001, 1, 10003, 'USER_LIST',          '查询用户列表',   'BUTTON', NULL, 'VIEW', 1, 'ACTIVE', 0),
(20002, 1, 10003, 'USER_CREATE',        '创建用户',       'BUTTON', NULL, 'ALL',  2, 'ACTIVE', 0),
(20003, 1, 10003, 'USER_GET',           '查询用户详情',   'BUTTON', NULL, 'VIEW', 3, 'ACTIVE', 0),
(20004, 1, 10003, 'USER_STATUS',        '变更账号状态',   'BUTTON', NULL, 'ALL',  4, 'ACTIVE', 0),
(20005, 1, 10004, 'ROLE_LIST',          '查询角色列表',   'BUTTON', NULL, 'VIEW', 1, 'ACTIVE', 0),
(20006, 1, 10004, 'ROLE_CREATE',        '新建角色',       'BUTTON', NULL, 'ALL',  2, 'ACTIVE', 0),
(20007, 1, 10004, 'ROLE_GET',           '查询角色详情',   'BUTTON', NULL, 'VIEW', 3, 'ACTIVE', 0),
(20008, 1, 10005, 'ROLE_ASSIGN',        '分配用户角色',   'BUTTON', NULL, 'ALL',  1, 'ACTIVE', 0),
(20009, 1, 10005, 'ROLE_REVOKE',        '撤销用户角色',   'BUTTON', NULL, 'ALL',  2, 'ACTIVE', 0),
(20010, 1, 10005, 'ROLE_ASSIGN_LIST',   '查询角色分配',   'BUTTON', NULL, 'VIEW', 3, 'ACTIVE', 0),
(20011, 1, 10006, 'SYSTEM_LIST',        '查询子系统',     'BUTTON', NULL, 'VIEW', 1, 'ACTIVE', 0),
(20012, 1, 10006, 'SYSTEM_CREATE',      '注册子系统',     'BUTTON', NULL, 'ALL',  2, 'ACTIVE', 0),
(20013, 1, 10006, 'PERMISSION_CREATE',  '登记菜单/按钮',  'BUTTON', NULL, 'ALL',  3, 'ACTIVE', 0),
(20014, 1, 10006, 'API_CREATE',         '登记后端 API',   'BUTTON', NULL, 'ALL',  4, 'ACTIVE', 0),
(20015, 1, 10005, 'ROLE_PERM_BIND',     '角色授权',       'BUTTON', NULL, 'ALL',  4, 'ACTIVE', 0),
(20016, 1, 10005, 'ROLE_PERM_UNBIND',   '角色取消授权',   'BUTTON', NULL, 'ALL',  5, 'ACTIVE', 0),
(20017, 1, 10005, 'ROLE_PERM_LIST',     '查询角色权限',   'BUTTON', NULL, 'VIEW', 6, 'ACTIVE', 0),
(20018, 1, 10002, 'ORG_TREE',           '查询组织树',     'BUTTON', NULL, 'VIEW', 1, 'ACTIVE', 0),
(20019, 1, 10002, 'ORG_CREATE',         '新建组织',       'BUTTON', NULL, 'ALL',  2, 'ACTIVE', 0),
(20020, 1, 10002, 'ORG_GET',            '查询组织详情',   'BUTTON', NULL, 'VIEW', 3, 'ACTIVE', 0),
(20021, 1, 10002, 'ORG_CHILDREN',       '查询下级组织',   'BUTTON', NULL, 'VIEW', 4, 'ACTIVE', 0),
(20022, 1, 10002, 'ORG_MOVE',           '移动组织节点',   'BUTTON', NULL, 'ALL',  5, 'ACTIVE', 0),
(20023, 1, 10006, 'PERMISSION_LIST',    '查询权限树',     'BUTTON', NULL, 'VIEW', 5, 'ACTIVE', 0),
(20024, 1, 10006, 'API_LIST',           '查询后端 API',   'BUTTON', NULL, 'VIEW', 6, 'ACTIVE', 0),
(20025, 1, 10006, 'API_PERM_BIND',      'API 映射权限',   'BUTTON', NULL, 'ALL',  7, 'ACTIVE', 0),
(20026, 1, 10006, 'API_PERM_UNBIND',    '解除 API 映射',  'BUTTON', NULL, 'ALL',  8, 'ACTIVE', 0),
(20027, 1, 10006, 'API_PERM_LIST',      '查询权限 API',   'BUTTON', NULL, 'VIEW', 9, 'ACTIVE', 0),
(20028, 1, 10006, 'SYSTEM_GET',         '查询系统详情',   'BUTTON', NULL, 'VIEW', 10, 'ACTIVE', 0),
(20029, 1, 10008, 'LOG_LIST',           '查询统一日志',   'BUTTON', NULL, 'VIEW', 1, 'ACTIVE', 0);

INSERT INTO iam_permission
    (id, system_id, parent_id, permission_code, permission_name, permission_type, route_path, action, sort_no, status, is_deleted)
VALUES
(20030, 1, 10007, 'DATA_SCOPE_LIST',      '查询数据范围字典', 'BUTTON', NULL, 'VIEW', 1, 'ACTIVE', 0),
(20031, 1, 10007, 'DATA_SCOPE_CREATE',    '创建数据范围字典', 'BUTTON', NULL, 'ALL',  2, 'ACTIVE', 0),
(20032, 1, 10007, 'ROLE_DATA_SCOPE_LIST', '查询角色数据范围', 'BUTTON', NULL, 'VIEW', 3, 'ACTIVE', 0),
(20033, 1, 10007, 'ROLE_DATA_SCOPE_BIND', '绑定角色数据范围', 'BUTTON', NULL, 'ALL',  4, 'ACTIVE', 0),
(20034, 1, 10007, 'ROLE_DATA_SCOPE_REVOKE', '回收角色数据范围', 'BUTTON', NULL, 'ALL', 5, 'ACTIVE', 0),
(20035, 1, 10004, 'ROLE_UPDATE',          '修改角色与继承',   'BUTTON', NULL, 'ALL',  4, 'ACTIVE', 0),
(20036, 1, 10004, 'ROLE_EXCLUSION_MANAGE','管理角色互斥',     'BUTTON', NULL, 'ALL',  5, 'ACTIVE', 0),
(20037, 1, 10002, 'ORG_UPDATE',           '修改和停用组织',   'BUTTON', NULL, 'ALL',  6, 'ACTIVE', 0),
(20038, 1, 10003, 'USER_TRANSFER',        '员工调岗',         'BUTTON', NULL, 'ALL',  5, 'ACTIVE', 0),
(20039, 1, 10003, 'USER_SENSITIVE_VIEW',  '查看敏感信息明文', 'BUTTON', NULL, 'ALL',  6, 'ACTIVE', 0);

-- 4. 后端 API：独立主键，通过 permission_id 关联一个前端菜单/按钮权限
INSERT INTO iam_api
    (id, system_id, permission_id, api_code, api_name, api_path, http_method, status, is_deleted)
VALUES
(30001, 1, 20001, 'USER_LIST',        '查询用户列表', '/users', 'GET', 'ACTIVE', 0),
(30002, 1, 20002, 'USER_CREATE',      '创建用户',     '/users', 'POST', 'ACTIVE', 0),
(30003, 1, 20003, 'USER_GET',         '查询用户详情', '/users/{userId}', 'GET', 'ACTIVE', 0),
(30004, 1, 20004, 'USER_STATUS',      '变更账号状态', '/users/{userId}/status', 'PUT', 'ACTIVE', 0),
(30005, 1, 20005, 'ROLE_LIST',        '查询角色列表', '/roles', 'GET', 'ACTIVE', 0),
(30006, 1, 20006, 'ROLE_CREATE',      '新建角色',     '/roles', 'POST', 'ACTIVE', 0),
(30007, 1, 20007, 'ROLE_GET',         '查询角色详情', '/roles/{roleId}', 'GET', 'ACTIVE', 0),
(30008, 1, 20008, 'ROLE_ASSIGN',      '分配用户角色', '/roles/{roleId}/assignments', 'POST', 'ACTIVE', 0),
(30009, 1, 20009, 'ROLE_REVOKE',      '撤销用户角色', '/roles/{roleId}/assignments/{userId}', 'DELETE', 'ACTIVE', 0),
(30010, 1, 20010, 'ROLE_ASSIGN_LIST', '查询角色分配', '/roles/{roleId}/assignments', 'GET', 'ACTIVE', 0),
(30011, 1, 20010, 'USER_ROLE_LIST',   '查询用户角色', '/roles/assignments', 'GET', 'ACTIVE', 0),
(30012, 1, 20011, 'SYSTEM_LIST',      '查询子系统',   '/systems', 'GET', 'ACTIVE', 0),
(30013, 1, 20012, 'SYSTEM_CREATE',    '注册子系统',   '/systems', 'POST', 'ACTIVE', 0),
(30014, 1, 20028, 'SYSTEM_GET',       '查询系统详情', '/systems/{systemId}', 'GET', 'ACTIVE', 0),
(30015, 1, 20013, 'PERMISSION_CREATE','登记权限',     '/permissions', 'POST', 'ACTIVE', 0),
(30016, 1, 20023, 'PERMISSION_LIST',  '查询权限树',   '/permissions', 'GET', 'ACTIVE', 0),
(30017, 1, 20014, 'API_CREATE',       '登记 API',     '/apis', 'POST', 'ACTIVE', 0),
(30018, 1, 20024, 'API_LIST',         '查询 API',     '/apis', 'GET', 'ACTIVE', 0),
(30019, 1, 20024, 'API_GET',          '查询 API 详情','/apis/{apiId}', 'GET', 'ACTIVE', 0),
(30020, 1, 20025, 'API_PERM_BIND',    '映射 API',     '/permissions/{permissionId}/apis/{apiId}', 'POST', 'ACTIVE', 0),
(30021, 1, 20026, 'API_PERM_UNBIND',  '解除 API 映射','/permissions/{permissionId}/apis/{apiId}', 'DELETE', 'ACTIVE', 0),
(30022, 1, 20027, 'API_PERM_LIST',    '查询权限 API', '/permissions/{permissionId}/apis', 'GET', 'ACTIVE', 0),
(30023, 1, 20015, 'ROLE_PERM_BIND',   '角色授权',     '/roles/{roleId}/permissions', 'POST', 'ACTIVE', 0),
(30024, 1, 20016, 'ROLE_PERM_UNBIND', '角色取消授权', '/roles/{roleId}/permissions', 'DELETE', 'ACTIVE', 0),
(30025, 1, 20017, 'ROLE_PERM_LIST',   '查询角色权限', '/roles/{roleId}/permissions', 'GET', 'ACTIVE', 0),
(30026, 1, 20018, 'ORG_TREE',         '查询组织树',   '/organizations', 'GET', 'ACTIVE', 0),
(30027, 1, 20019, 'ORG_CREATE',       '新建组织',     '/organizations', 'POST', 'ACTIVE', 0),
(30028, 1, 20020, 'ORG_GET',          '查询组织详情', '/organizations/{orgId}', 'GET', 'ACTIVE', 0),
(30029, 1, 20021, 'ORG_CHILDREN',     '查询下级组织', '/organizations/{orgId}/children', 'GET', 'ACTIVE', 0),
(30030, 1, 20022, 'ORG_MOVE',         '移动组织节点', '/organizations/{orgId}/parent', 'PUT', 'ACTIVE', 0),
(30031, 1, 20029, 'LOG_LIST',         '查询统一日志', '/logs', 'GET', 'ACTIVE', 0);

INSERT INTO iam_api
    (id, system_id, permission_id, api_code, api_name, api_path, http_method, status, is_deleted)
VALUES
(30032, 1, 20030, 'DATA_SCOPE_LIST',      '查询数据范围字典', '/systems/data-scopes', 'GET', 'ACTIVE', 0),
(30033, 1, 20031, 'DATA_SCOPE_CREATE',    '创建数据范围字典', '/systems/data-scopes', 'POST', 'ACTIVE', 0),
(30034, 1, 20032, 'ROLE_DATA_SCOPE_LIST', '查询角色数据范围', '/roles/{roleId}/data-scopes', 'GET', 'ACTIVE', 0),
(30035, 1, 20033, 'ROLE_DATA_SCOPE_BIND', '绑定角色数据范围', '/roles/{roleId}/data-scopes', 'POST', 'ACTIVE', 0),
(30036, 1, 20034, 'ROLE_DATA_SCOPE_REVOKE', '回收角色数据范围', '/roles/{roleId}/data-scopes/{bindingId}', 'DELETE', 'ACTIVE', 0),
(30037, 1, 20035, 'ROLE_UPDATE',          '修改角色与继承',   '/roles/{roleId}', 'PUT', 'ACTIVE', 0),
(30038, 1, 20036, 'ROLE_EXCLUSION_LIST',  '查询角色互斥',     '/roles/{roleId}/exclusions', 'GET', 'ACTIVE', 0),
(30039, 1, 20036, 'ROLE_EXCLUSION_BIND',  '新建角色互斥',     '/roles/{roleId}/exclusions', 'POST', 'ACTIVE', 0),
(30040, 1, 20036, 'ROLE_EXCLUSION_UNBIND','删除角色互斥',     '/roles/{roleId}/exclusions/{excludedRoleId}', 'DELETE', 'ACTIVE', 0),
(30041, 1, 20037, 'ORG_UPDATE',           '修改和停用组织',   '/organizations/{orgId}', 'PUT', 'ACTIVE', 0),
(30042, 1, 20038, 'USER_TRANSFER',        '员工调岗',         '/users/{userId}/organization', 'PUT', 'ACTIVE', 0),
(30043, 1, 20031, 'ROLE_DATA_SCOPE_LEGACY', '兼容角色数据范围入口', '/systems/role-data-scopes', 'POST', 'ACTIVE', 0);
INSERT INTO iam_api
    (id, system_id, permission_id, api_code, api_name, api_path, http_method, status, is_deleted)
VALUES
(30044, 1, 20029, 'LOG_EXPORT', '导出统一日志', '/logs/export', 'GET', 'ACTIVE', 0);
INSERT INTO iam_api
    (id, system_id, permission_id, api_code, api_name, api_path, http_method, status, is_deleted)
VALUES
(30045, 1, 20039, 'USER_SENSITIVE_VIEW', '查看敏感信息明文',
 '/users/{userId}/sensitive', 'GET', 'ACTIVE', 0);

-- 5. RBAC1/RBAC3 基线功能角色与岗位型数据角色
INSERT INTO iam_role (id, role_code, role_name, role_type, status, description, is_deleted)
VALUES
(1, 'SUPER_ADMIN',      '超级管理员', 'FUNC', 'ACTIVE', '拥有管理后台全部前端权限', 0),
(2, 'COMPANY_LEADER',   '公司领导',   'FUNC', 'ACTIVE', '跨分公司管理角色基线', 0),
(3, 'DEPT_MANAGER',     '部门经理',   'FUNC', 'ACTIVE', '部门人员与权限管理角色基线', 0),
(4, 'PROJECT_MANAGER',  '项目经理',   'FUNC', 'ACTIVE', '项目经理部管理角色基线', 0),
(5, 'EMPLOYEE',         '普通员工',   'FUNC', 'ACTIVE', '普通业务操作角色基线', 0);

INSERT INTO iam_role (id, role_code, role_name, role_type, status, description, is_deleted, parent_id)
VALUES
(6, 'DATA_SAFETY',      '安全员',     'DATA', 'ACTIVE', '岗位型数据角色基线', 5),
(7, 'DATA_BUDGET',      '预算员',     'DATA', 'ACTIVE', '岗位型数据角色基线', 5),
(8, 'DATA_QUALITY',     '质检员',     'DATA', 'ACTIVE', '岗位型数据角色基线', 5),
(9, 'DATA_COST',        '造价员',     'DATA', 'ACTIVE', '岗位型数据角色基线', 5),
(10, 'DATA_ACCOUNTANT', '会计',       'DATA', 'ACTIVE', '与出纳互斥', 5),
(11, 'DATA_CASHIER',    '出纳',       'DATA', 'ACTIVE', '与会计互斥', 5);

INSERT INTO iam_role_permission (role_id, permission_id, allowed_action)
SELECT 1, id, action FROM iam_permission WHERE system_id = 1 AND is_deleted = 0;

-- 会计与出纳为静态互斥角色。
INSERT INTO iam_role_exclusion (role_id, excluded_role_id, created_by)
VALUES (10, 11, 1);

-- 7. 数据范围字典
INSERT INTO iam_data_scope (id, scope_code, scope_name, description, sort_no, status, is_deleted)
VALUES
(1, 'ALL',     '全公司', '可访问接入系统内全部数据', 1, 'ACTIVE', 0),
(2, 'BRANCH',  '本分公司', '覆盖授权分公司及其下级节点', 2, 'ACTIVE', 0),
(3, 'DEPT',    '本部门', '覆盖授权部门及其下级项目节点', 3, 'ACTIVE', 0),
(4, 'PROJECT', '本项目', '按项目 ID 精确过滤', 4, 'ACTIVE', 0),
(5, 'SELF',    '本人', '仅目标用户等于当前用户', 5, 'ACTIVE', 0);

-- 8. 分层组织：总公司 → 信息中心
INSERT INTO iam_org
    (id, parent_id, ancestors, org_code, org_name, org_type, org_level, sort_no, status, is_deleted)
VALUES
(1, NULL, '0',   'HQ',      '总公司',   'HQ',   1, 0, 'ACTIVE', 0),
(2, 1,    '0,1', 'HQ_INFO', '信息中心', 'DEPT', 3, 1, 'ACTIVE', 0);

-- 9. 管理员账号 admin / Admin@123
INSERT INTO iam_user
    (id, user_no, password_hash, name_enc, name_hash, org_id, account_status, is_deleted)
VALUES (1, 'admin',
        'tixOSPFdKOrb2MmPToW+Lw==:C6/muNPbyI7jZnvTebxp3+ToGlVmk0/jm6wtw1pD/zg=',
        'B4OPGWRD4NL5lmpg7NhNXVGayADahKNOd8oyfZhK9BvI9y3A52MPGnhdtw==',
        '40d3d919f122836c04add2bf70ac897d6ca8fe5947ab539ccf5e161d817a1d19',
        1, 'NORMAL', 0);

INSERT INTO iam_user_role (user_id, role_id, effective_time, grant_reason, status)
VALUES (1, 1, NOW(), '系统初始化', 'ACTIVE');

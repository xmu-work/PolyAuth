"""Sequence diagrams for the seven paired business functions.

Messages follow the actual Java call chains in ``structured-backend`` and
``oop-backend``.  ``SeqFlow`` assigns monotonic y coordinates, creates one
execution specification per synchronous call and computes fragment bounds from
the activations/messages inside them.
"""
from __future__ import annotations

from uml_layout import SeqFlow


# ---------------------------------------------------------------------------
# 1. 认证
# ---------------------------------------------------------------------------

def oop_login_sequence():
    s = SeqFlow(
        "面向对象后端 · 登录认证",
        "AuthController → AuthenticationUseCase；密码、令牌签发与哈希、审计和时钟全部通过应用端口调用。",
        "alt 区分成功与凭证失败；TokenSession 为静态工厂（MetaClass）。异常从仍激活的用例执行条直接返回控制器。",
    )
    s.lifelines([
        ("ui", "web", "LoginPage", 230),
        ("controller", "authController", "AuthController", 300),
        ("auth", "authenticationUseCase", "AuthenticationUseCase", 400),
        ("user_repo", "userRepository", "UserAccountRepository", 320),
        ("password", "passwordVerifier", "PasswordVerifier", 290),
        ("issuer", "tokenIssuer", "AccessTokenIssuer", 300),
        ("clock", "clock", "Clock", 200),
        ("hasher", "tokenSecretHasher", "TokenSecretHasher", 310),
        ("session", "session", "TokenSession", 290, True),
        ("token_repo", "tokenRepository", "TokenSessionRepository", 330),
        ("audit", "auditTrail", "AuditTrail", 280),
    ])
    s.root("ui", "controller")
    s.entry("ui", "controller", "login(username, password, systems)")
    act = s.begin("controller", "auth",
                  "authenticationUseCase.login(command)", height=74)

    with s.fragment("alt", "[账号存在且密码正确]"):
        s.call("auth", "user_repo", "findByUserNo(username)",
               "Optional<UserAccount>(user)")
        s.call("auth", "password", "verify(password, passwordHash)", "true")
        s.call("auth", "issuer", "issue(userNo, systems)",
               "IssuedToken(value, expiresIn)")
        s.call("auth", "clock", "instant()", "Instant now")
        s.call("auth", "hasher", "hash(issued.value())", "tokenHash")
        s.call("auth", "session",
               "TokenSession.issue(id, userId, systemCode, tokenHash, now, expireAt, ip)",
               "session")
        s.fire("auth", "token_repo", "save(session)")
        s.call("auth", "user_repo", "save(user.recordLogin(now))",
               "UserAccount(updated)")
        s.fire("auth", "audit",
               "recordLogin(userId, userNo, systemCode, true, null, ip, requestId)")
        s.reply_from(act, "controller",
                     "LoginResult(accessToken, expiresIn, displayName, accountStatus)")
        s.reply_to("controller", "ui", "200 LoginResult")

        s.branch("[账号不存在或密码错误]")
        s.call("auth", "user_repo", "findByUserNo(username)",
               "Optional.empty() / 过滤后为空")
        s.fire("auth", "audit",
               'recordLogin(null, username, systemCode, false, "2001", ip, requestId)')
        s.reply_from(act, "controller",
                     "UseCaseException(UNAUTHENTICATED, 2001)", exception=True)
        s.reply_to("controller", "ui", "401 账号或密码错误")

    s.end("auth")
    return s.finish()


# ---------------------------------------------------------------------------
# 2. 鉴权决策（OOP 细部已单独成页，这里只补结构化缺失的 class 由已有页面提供）
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# 3. 角色授权
# ---------------------------------------------------------------------------

def oop_role_grant_sequence():
    s = SeqFlow(
        "面向对象后端 · 角色权限授予",
        "RoleController → RoleManagementUseCase.bindPermissions；角色与权限事实来自领域仓储端口。",
        "loop 框住每一轮 bind 调用；绑定前后的集合快照不同才写入权限变更审计。",
    )
    s.lifelines([
        ("client", "bindPanel", "RolePermissionBindCard", 290),
        ("controller", "roleController", "RoleController", 280),
        ("role", "roleManagementUseCase", "RoleManagementUseCase", 400),
        ("role_repo", "roleRepository", "RoleRepository", 280),
        ("binding_repo", "rolePermissionRepository", "RolePermissionRepository", 350),
        ("catalog", "permissionCatalogRepository", "PermissionCatalogRepository", 350),
        ("audit", "auditTrail", "AuditTrail", 300),
    ])
    s.root("client", "controller")
    s.entry("client", "controller",
            "bindPermissions(roleId, permissionIds, allowedAction)")
    act = s.begin("controller", "role",
                  "bindPermissions(roleId, ids, action, operatorId)", height=72)
    s.call("role", "role_repo", "findById(roleId)", "Role(status=ACTIVE)")
    s.call("role", "binding_repo", "findByRoleId(roleId)",
           "before: List<RolePermissionGrant>")
    s.call("role", "catalog", "findFacts(permissionIds)",
           "List<PermissionFact>")

    with s.fragment("loop", "for each permissionId"):
        s.fire("role", "binding_repo",
               "bind(roleId, permissionId, action, operatorId)", height=58)

    s.note("binding_repo", 6, 300,
           ["fact 缺失或停用：", "RESOURCE_NOT_FOUND / PARAM_INVALID"])

    s.call("role", "binding_repo", "findByRoleId(roleId)",
           "after: List<RolePermissionGrant>")
    with s.fragment("opt", "!before.equals(after)"):
        s.fire("role", "audit",
               "recordPermissionChange(..., BIND_ROLE_PERMISSION, ...)", height=56)
    s.end("role", "void")
    s.reply_to("controller", "client", "200 ApiResult.success()")
    return s.finish()


# ---------------------------------------------------------------------------
# 4. 账号管理
# ---------------------------------------------------------------------------

def structured_account_status_sequence():
    s = SeqFlow(
        "结构化后端 · 账号状态变更与级联回收",
        "AccountService 在同一事务中修改状态，并按目标状态吊销令牌、回收角色，最后写权限变更审计。",
        "opt 表示两条独立的附加动作；SEALED 同时触发令牌与角色回收。",
    )
    s.lifelines([
        ("client", "userAdmin", "UserAdminPage", 240),
        ("controller", "userController", "UserController", 280),
        ("account", "accountService", "AccountService", 360),
        ("user_repo", "userRepository", "IamUserRepository", 330),
        ("auth", "authService", "AuthService", 300),
        ("token_repo", "tokenRepository", "IamTokenRepository", 330),
        ("role", "roleService", "RoleService", 300),
        ("grant_repo", "userRoleRepository", "IamUserRoleRepository", 350),
        ("log", "logService", "LogService", 280),
    ])
    s.root("client", "controller")
    s.entry("client", "controller", "changeStatus(userId, targetStatus, reason)")
    act = s.begin("controller", "account",
                  "changeStatus(userId, targetStatus, reason, operatorId)",
                  height=74)
    s.call("account", "user_repo", "findById(userId)", "IamUser(undeleted)")

    with s.fragment("opt", "targetStatus != NORMAL"):
        s.begin("account", "auth", "revokeUserTokens(userId)")
        s.call("auth", "token_repo", "findByUserIdAndStatus(userId, ACTIVE)",
               "List<IamToken>")
        with s.fragment("loop", "for each token"):
            s.fire("auth", "token_repo", "save(token.revoke())", height=54)
        s.end("auth", "void")

    with s.fragment("opt", "targetStatus == SEALED"):
        s.begin("account", "role", "revokeAllRoles(userId, operatorId)")
        s.call("role", "grant_repo", "findByUserIdAndStatus(userId, ACTIVE)",
               "List<IamUserRole>")
        with s.fragment("loop", "for each grant"):
            s.fire("role", "grant_repo", "save(grant.revoke())", height=48)
            s.fire("role", "log",
                   "recordPermissionChange(..., REVOKE_ROLE, ...)", height=52)
        s.end("role", "void")

    s.call("account", "user_repo", "save(user)", "IamUser saved")
    s.fire("account", "log",
           "recordPermissionChange(..., CHANGE_USER_STATUS, before, after)", height=60)
    s.end("account", "UserView")
    s.reply_to("controller", "client", "200 UserVO")
    return s.finish()


def oop_account_status_sequence():
    s = SeqFlow(
        "面向对象后端 · 账号状态变更与级联回收",
        "AccountManagementUseCase 编排读取、领域状态迁移、令牌回收与授权回收；状态规则由 UserAccount 承担。",
        "UserAccount.changeStatus 返回新的账号快照；SEALED 时逐个 grant.revoke(now) 后保存。",
    )
    s.lifelines([
        ("client", "userAdmin", "UserAdminPage", 240),
        ("controller", "userController", "UserController", 280),
        ("account", "accountUseCase", "AccountManagementUseCase", 400),
        ("user_repo", "userRepository", "UserAccountRepository", 340),
        ("user", "user", "UserAccount", 300),
        ("clock", "clock", "Clock", 200),
        ("token_repo", "tokenRepository", "TokenSessionRepository", 340),
        ("grant_repo", "roleGrantRepository", "RoleGrantRepository", 330),
        ("audit", "auditTrail", "AuditTrail", 300),
    ])
    s.root("client", "controller")
    s.entry("client", "controller", "changeStatus(userId, targetStatus, reason)")
    act = s.begin("controller", "account",
                  "changeStatus(userId, targetStatus, reason, operatorId)",
                  height=74)
    s.call("account", "user_repo", "findById(userId)", "UserAccount(undeleted)")
    s.call("account", "clock", "instant()", "Instant now")
    s.call("account", "user", "changeStatus(target, operatorId, now)",
           "changed: UserAccount")
    s.call("account", "user_repo", "save(changed)", "saved: UserAccount")

    with s.fragment("opt", "targetStatus != NORMAL"):
        s.fire("account", "token_repo", "revokeAllForUser(userId, now)", height=56)

    with s.fragment("opt", "targetStatus == SEALED"):
        s.call("account", "grant_repo", "findByUserId(userId)",
               "List<RoleGrant>(ACTIVE)")
        with s.fragment("loop", "for each grant"):
            s.fire("account", "grant_repo", "save(grant.revoke(now))", height=52)

    s.fire("account", "audit",
           "recordPermissionChange(..., CHANGE_USER_STATUS, before, after)",
           height=60)
    s.end("account", "void")
    s.reply_to("controller", "client", "200 UserVO")
    return s.finish()


# ---------------------------------------------------------------------------
# 5. 组织管理
# ---------------------------------------------------------------------------

def structured_org_move_sequence():
    s = SeqFlow(
        "结构化后端 · 组织移动",
        "OrganizationService 校验目标组织后，重写整棵子树的 ancestors 前缀并批量保存。",
        "alt 区分非法移动（ORG_MOVE_INVALID）与合法移动；子树在内存中重写后 saveAll。",
    )
    s.lifelines([
        ("client", "orgAdmin", "OrganizationTreePage", 280),
        ("controller", "organizationController", "OrganizationController", 320),
        ("org", "organizationService", "OrganizationService", 360),
        ("repo", "orgRepository", "IamOrgRepository", 340),
    ])
    s.root("client", "controller")
    s.entry("client", "controller", "move(orgId, newParentId)")
    act = s.begin("controller", "org", "moveOrg(orgId, newParentId)", height=76)
    s.call("org", "repo", "findById(orgId)", "IamOrg node")
    s.call("org", "repo", "findById(newParentId)", "IamOrg newParent")

    with s.fragment("alt", "[目标不是自身或下级且层级合法]"):
        s.call("org", "repo", "findByAncestorsStartingWith(prefix)",
               "List<IamOrg> descendants")
        s.note("repo", 8, 300,
               ["descendants 在内存中重写", "ancestors 前缀"])
        s.call("org", "repo", "saveAll(descendants)", "List<IamOrg>")
        s.call("org", "repo", "save(node)", "IamOrg moved")
        s.reply_from(act, "controller", "IamOrg moved")
        s.reply_to("controller", "client", "200 OrganizationVO")

        s.branch("[移动到自身或下级 / 层级不合法]")
        s.reply_from(act, "controller",
                     "BusinessException(ORG_MOVE_INVALID)", exception=True)
        s.reply_to("controller", "client", "400 组织移动非法")

    s.end("org")
    return s.finish()


def oop_org_move_sequence():
    s = SeqFlow(
        "面向对象后端 · 组织移动",
        "OrganizationUseCase 读取节点与目标父节点，由 Organization.contains / reparent 承担树形规则。",
        "reparent 返回新的组织快照；后代通过 withAncestors 生成新前缀后 saveAll。",
    )
    s.lifelines([
        ("client", "orgAdmin", "OrganizationTreePage", 280),
        ("controller", "organizationController", "OrganizationController", 320),
        ("uc", "organizationUseCase", "OrganizationUseCase", 380),
        ("repo", "organizationRepository", "OrganizationRepository", 340),
        ("node", "node", "Organization", 280),
        ("parent", "parent", "Organization", 280),
        ("audit", "auditTrail", "AuditTrail", 300),
    ])
    s.root("client", "controller")
    s.entry("client", "controller", "move(orgId, newParentId)")
    act = s.begin("controller", "uc", "move(orgId, newParentId, operatorId)",
                  height=78)
    s.call("uc", "repo", "findById(orgId)", "Organization node")
    s.call("uc", "repo", "findById(newParentId)", "Organization parent")
    s.call("uc", "parent", "contains(orgId)", "boolean")

    with s.fragment("alt", "[contains == false 且层级合法]"):
        s.call("uc", "node", "selfPath()", "oldPrefix")
        s.call("uc", "repo", "findByAncestorsPrefix(prefix)",
               "List<Organization> descendants")
        s.call("uc", "node", "reparent(parent, operatorId, now)", "moved")
        s.call("uc", "repo", "save(moved)", "Organization moved")
        s.call("uc", "node", "selfPath()", "newPrefix")
        s.call("uc", "repo", "saveAll(descendants.withAncestors(...))",
               "List<Organization>")
        s.fire("uc", "audit",
               "recordPermissionChange(..., MOVE_ORGANIZATION, ...)", height=58)
        s.reply_from(act, "controller", "Organization moved")
        s.reply_to("controller", "client", "200 OrganizationVO")

        s.branch("[contains == true / 层级不合法]")
        s.reply_from(act, "controller",
                     "DomainRuleException(ORGANIZATION_INVALID_MOVE)",
                     exception=True)
        s.reply_to("controller", "client", "400 组织移动非法")

    s.end("uc")
    return s.finish()


# ---------------------------------------------------------------------------
# 6. 权限与目录管理
# ---------------------------------------------------------------------------

def structured_permission_bind_sequence():
    s = SeqFlow(
        "结构化后端 · 权限绑定 API",
        "PermissionController → PermissionService.bindApi：读取权限与 API，校验同系统后回写 permissionId。",
        "alt 区分系统一致与不一致；绑定只更新 API 一侧的外键，权限本身不变。",
    )
    s.lifelines([
        ("client", "permissionAdmin", "PermissionApiBindPage", 300),
        ("controller", "permissionController", "PermissionController", 320),
        ("perm", "permissionService", "PermissionService", 340),
        ("perm_repo", "permissionRepository", "IamPermissionRepository", 350),
        ("api_repo", "apiRepository", "IamApiRepository", 330),
    ])
    s.root("client", "controller")
    s.entry("client", "controller", "bindApi(permissionId, apiId)")
    act = s.begin("controller", "perm", "bindApi(permissionId, apiId)", height=76)
    s.call("perm", "perm_repo", "findById(permissionId)", "IamPermission")
    s.call("perm", "api_repo", "findById(apiId)", "IamApi")

    with s.fragment("alt", "[权限与 API 同属一个系统]"):
        s.call("perm", "api_repo", "save(api.setPermissionId(permissionId))",
               "IamApi")
        s.reply_from(act, "controller", "ApiVO")
        s.reply_to("controller", "client", "200 ApiVO")

        s.branch("[系统不一致]")
        s.reply_from(act, "controller",
                     "BusinessException(PARAM_INVALID)", exception=True)
        s.reply_to("controller", "client", "400 权限与 API 必须属于同一系统")

    s.end("perm")
    return s.finish()


def oop_permission_bind_sequence():
    s = SeqFlow(
        "面向对象后端 · 权限绑定 API",
        "PermissionController → PermissionCatalogUseCase.bindApi；ApiEndpoint.mapPermission 返回新的 API 快照。",
        "alt 区分系统一致与不一致；用例只依赖管理侧领域端口。",
    )
    s.lifelines([
        ("client", "permissionAdmin", "PermissionApiBindPage", 300),
        ("controller", "permissionController", "PermissionController", 320),
        ("uc", "permissionCatalogUseCase", "PermissionCatalogUseCase", 400),
        ("perm_repo", "permissionRepository", "PermissionAdministrationRepository", 380),
        ("api_repo", "apiRepository", "ApiAdministrationRepository", 350),
    ])
    s.root("client", "controller")
    s.entry("client", "controller", "bindApi(permissionId, apiId)")
    act = s.begin("controller", "uc", "bindApi(permissionId, apiId)", height=76)
    s.call("uc", "perm_repo", "findById(permissionId)", "PermissionNode")
    s.call("uc", "api_repo", "findById(apiId)", "ApiEndpoint")

    with s.fragment("alt", "[permission.systemId == api.systemId]"):
        s.call("uc", "api_repo",
               "save(api.mapPermission(permissionId, now))", "ApiEndpoint")
        s.reply_from(act, "controller", "ApiVO")
        s.reply_to("controller", "client", "200 ApiVO")

        s.branch("[系统不一致]")
        s.reply_from(act, "controller",
                     "UseCaseException(PARAM_INVALID)", exception=True)
        s.reply_to("controller", "client", "400 权限与 API 必须属于同一系统")

    s.end("uc")
    return s.finish()


# ---------------------------------------------------------------------------
# 7. 审计日志
# ---------------------------------------------------------------------------

def structured_audit_sequence():
    s = SeqFlow(
        "结构化后端 · 审计日志查询与导出",
        "LogController 复用 LogService：分页查询走 Specification + Pageable，导出复用同一 Specification。",
        "alt 区分分页查询与 CSV 导出；导出上限 100000 行，超限返回 PARAM_INVALID。",
    )
    s.lifelines([
        ("client", "auditAdmin", "AuditLogPage", 250),
        ("controller", "logController", "LogController", 300),
        ("log", "logService", "LogService", 340),
        ("repo", "logRepository", "IamLogRepository", 350),
    ])
    s.root("client", "controller")
    s.entry("client", "controller", "GET /logs 或 GET /logs/export")

    with s.fragment("alt", "[分页查询]"):
        act = s.begin("controller", "log", "queryLogs(pageable, filters)", height=76)
        with s.fragment("opt", "logType / success / 时间范围非法"):
            s.reply_from(act, "controller",
                         "BusinessException(PARAM_INVALID)", exception=True)
            s.reply_to("controller", "client", "400 查询参数非法")
        s.call("log", "repo", "findAll(specification, pageable)", "Page<IamLog>")
        s.reply_from(act, "controller", "PageResult<LogVO>")
        s.reply_to("controller", "client", "200 PageResult<LogVO>")
        s.end("log")

        s.branch("[导出 CSV]")
        act2 = s.begin("controller", "log", "exportLogs(filters)", height=76)
        with s.fragment("opt", "记录数 > 100000"):
            s.reply_from(act2, "controller",
                         "BusinessException(PARAM_INVALID)", exception=True)
            s.reply_to("controller", "client", "400 导出范围过大")
        s.call("log", "repo", "findAll(specification, page 0..100001)",
               "List<IamLog>")
        s.reply_from(act2, "controller", "byte[] CSV（含 BOM）")
        s.reply_to("controller", "client", "200 text/csv attachment")
        s.end("log")
    return s.finish()


def oop_audit_sequence():
    s = SeqFlow(
        "面向对象后端 · 审计日志查询与导出",
        "LogController 构造 AuditQuery → AuditQueryUseCase.page/export → AuditQueryRepository。",
        "alt 区分分页与导出；validate 统一规范化筛选条件，导出上限 100000 行。",
    )
    s.lifelines([
        ("client", "auditAdmin", "AuditLogPage", 250),
        ("controller", "logController", "LogController", 300),
        ("uc", "auditQueryUseCase", "AuditQueryUseCase", 360),
        ("repo", "auditQueryRepository", "AuditQueryRepository", 360),
    ])
    s.root("client", "controller")
    s.entry("client", "controller", "GET /logs 或 GET /logs/export")

    with s.fragment("alt", "[分页查询]"):
        act = s.begin("controller", "uc", "page(query, page, size)", height=76)
        with s.fragment("opt", "query 非法"):
            s.reply_from(act, "controller",
                         "UseCaseException(PARAM_INVALID)", exception=True)
            s.reply_to("controller", "client", "400 查询参数非法")
        s.call("uc", "repo", "findPage(query, page, size)",
               "DomainPage<AuditEntry>")
        s.reply_from(act, "controller", "LogPageVO")
        s.reply_to("controller", "client", "200 ApiResult<LogPageVO>")
        s.end("uc")

        s.branch("[导出 CSV]")
        act2 = s.begin("controller", "uc", "export(query)", height=76)
        with s.fragment("opt", "记录数 > 100000"):
            s.reply_from(act2, "controller",
                         "UseCaseException(PARAM_INVALID)", exception=True)
            s.reply_to("controller", "client", "400 导出范围过大")
        s.call("uc", "repo", "findForExport(query, 100000)",
               "List<AuditEntry>")
        s.reply_from(act2, "controller", "List<AuditEntry>")
        s.end("uc")
        s.call("controller", "uc", "csv(entries)", "String")
        s.reply_to("controller", "client", "200 text/csv attachment")
    return s.finish()

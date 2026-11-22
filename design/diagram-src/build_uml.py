"""生成 RBAC-Structured 项目的 UML 类图与顺序图。

只用 Python 标准库生成 SVG，再调用 rsvg-convert 输出 2 倍 PNG。
图中的类、字段、方法与调用顺序均取自当前 Java / TypeScript 实现。
"""
from __future__ import annotations

import sys
from pathlib import Path

from umlkit import ClassDiagram, SequenceDiagram, render_png


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "design"


def structured_auth_class() -> ClassDiagram:
    d = ClassDiagram(
        1900,
        1160,
        "结构化后端 · 认证核心类图",
        "以 AuthService 为中心；对象字段形成带 role name 的关联，实体仅通过仓储签名参与。",
    )
    d.add_box(
        "controller", 120, 115, 540, "IdentityAuthController",
        ["- authService: AuthService", "- identityService: IdentityService"],
        ["+ login(LoginRequest, HttpServletRequest): LoginResponse",
         "+ logout(String): void", "+ currentUser(Long, String): IdentityCurrentUserVO"],
        "«controller»", "caller",
    )
    d.add_box(
        "interceptor", 1240, 115, 540, "AuthInterceptor",
        ["- authService: AuthService", "- apiService: ApiService",
         "- decisionService: DecisionService", "- logService: LogService"],
        ["+ preHandle(...): boolean", "+ afterCompletion(...): void"],
        "«component»", "caller",
    )
    d.add_box(
        "auth", 680, 470, 540, "AuthService",
        ["- userRepository: IamUserRepository", "- tokenRepository: IamTokenRepository",
         "- cryptoComponent: CryptoComponent", "- logService: LogService"],
        ["+ login(LoginRequest): LoginResponse", "+ logout(String): void",
         "+ authenticateSession(String): AuthenticatedSession",
         "+ revokeUserTokens(Long): void",
         "+ changePassword(Long, String, String): void"],
        "«service»", "service",
    )
    d.add_box(
        "crypto", 140, 520, 430, "CryptoComponent", [],
        ["+ verifyPassword(String, String): boolean", "+ hash(String): String",
         "+ hashPassword(String): String", "+ decrypt(String): String"],
        "«component»", "infra",
    )
    d.add_box(
        "log", 1330, 520, 430, "LogService", [],
        ["+ record(Long, String, String, ...): void",
         "+ recordPermissionChange(Long, ...): void"],
        "«service»", "infra",
    )
    d.add_box(
        "user_repo", 180, 875, 500, "IamUserRepository", [],
        ["+ findByUserNo(String): Optional<IamUser>",
         "+ findById(Long): Optional<IamUser>", "+ save(IamUser): IamUser",
         "+ findByOrgId(Long): List<IamUser>"],
        "«interface · Spring Data JPA»", "interface",
    )
    d.add_box(
        "token_repo", 1220, 875, 500, "IamTokenRepository", [],
        ["+ findByTokenHash(String): Optional<IamToken>",
         "+ findByUserIdAndStatus(Long, String): List<IamToken>",
         "+ save(IamToken): IamToken"],
        "«interface · Spring Data JPA»", "interface",
    )

    d.association(
        [(390, 306), (390, 392), (760, 392), (760, 470)],
        "authService", (575, 380), "controller", "auth",
    )
    d.association(
        [(1510, 327), (1510, 393), (1140, 393), (1140, 470)],
        "authService", (1325, 381), "interceptor", "auth",
    )
    d.association([(680, 590), (570, 590)], "cryptoComponent",
                  (625, 577), "auth", "crypto")
    d.association([(1220, 590), (1330, 590)], "logService",
                  (1275, 577), "auth", "log")
    d.association([(830, 745), (620, 830), (430, 875)],
                  "userRepository", (525, 842), "auth", "user_repo")
    d.association([(1070, 745), (1350, 830), (1470, 875)],
                  "tokenRepository", (1255, 842), "auth", "token_repo")

    d.footer = (
        "关联：实线空心箭头；role name 为实际字段名。"
        " LoginRequest、LoginResponse、IamUser、IamToken 作为方法参数/返回值参与签名。"
    )
    return d


def structured_decision_class() -> ClassDiagram:
    d = ClassDiagram(
        2280,
        1510,
        "结构化后端 · 权限决策类图",
        "以 DecisionService（PDP）为中心，调用方在四周，四个 Repository 在下方形成单条判定链。",
    )
    d.add_box(
        "interceptor", 90, 110, 500, "AuthInterceptor",
        ["- authService: AuthService", "- apiService: ApiService",
         "- decisionService: DecisionService", "- logService: LogService"],
        ["+ preHandle(...): boolean", "+ afterCompletion(...): void"],
        "«component»", "caller",
    )
    d.add_box(
        "identity", 720, 100, 620, "IdentityService",
        ["- userRepository: IamUserRepository", "- orgRepository: IamOrgRepository",
         "- apiRepository: IamApiRepository", "- systemRepository: IamSystemRepository",
         "- permissionRepository: IamPermissionRepository",
         "- decisionService: DecisionService", "- cryptoComponent: CryptoComponent"],
        ["+ getCurrentUser(Long, String): IdentityProfile",
         "+ hasPermissionCode(Long, String, String): boolean",
         "+ hasApiOperation(Long, String, String): boolean",
         "+ pageDepartments(...): IdentityPageSlice"],
        "«service»", "caller",
    )
    d.add_box(
        "open", 1480, 110, 500, "OpenApiController",
        ["- decisionService: DecisionService"],
        ["+ permissions(...): Result<Set<Long>>"],
        "«controller»", "caller",
    )
    d.add_box(
        "role_service", 1690, 620, 520, "RoleService",
        ["- roleRepository: IamRoleRepository", "- userRepository: IamUserRepository",
         "- userRoleRepository: IamUserRoleRepository",
         "- permissionRepository: IamPermissionRepository",
         "- rolePermissionRepository: IamRolePermissionRepository",
         "- roleExclusionRepository: IamRoleExclusionRepository",
         "- decisionService: DecisionService", "- logService: LogService",
         "- lockExecutor: BusinessLockExecutor"],
        ["+ grantRole(...): IamUserRole", "+ checkGrantBoundary(Long, Long)",
         "+ bindRolePermissions(...): void", "+ collectRolePermissionIds(Long)"],
        "«service»", "caller",
    )
    d.add_box(
        "decision", 760, 570, 700, "DecisionService",
        ["- userRepository: IamUserRepository",
         "- userRoleRepository: IamUserRoleRepository",
         "- roleRepository: IamRoleRepository",
         "- rolePermissionRepository: IamRolePermissionRepository"],
        ["+ hasPermission(Long, String, Long): boolean",
         "+ getUserPermissions(Long, String): Set<Long>",
         "+ getUserRoleCodes(Long): Set<String>", "- activeRoleIds(Long): Set<Long>",
         "- collectActiveRolesWithAncestors(Set<Long>): Set<Long>"],
        "«service»", "service",
    )
    d.add_box(
        "user_repo", 80, 1235, 440, "IamUserRepository", [],
        ["+ findById(Long): Optional<IamUser>",
         "+ findByUserNo(String): Optional<IamUser>",
         "+ save(IamUser): IamUser"],
        "«interface»", "interface",
    )
    d.add_box(
        "user_role_repo", 600, 1235, 460, "IamUserRoleRepository", [],
        ["+ findByUserIdAndStatus(Long, String): List<IamUserRole>",
         "+ findByUserIdAndRoleId(Long, Long): Optional<IamUserRole>",
         "+ save(IamUserRole): IamUserRole"],
        "«interface»", "interface",
    )
    d.add_box(
        "role_repo", 1140, 1235, 460, "IamRoleRepository", [],
        ["+ findAllById(Iterable<Long>): List<IamRole>",
         "+ findByRoleCode(String): Optional<IamRole>",
         "+ save(IamRole): IamRole"],
        "«interface»", "interface",
    )
    d.add_box(
        "role_perm_repo", 1740, 1235, 460, "IamRolePermissionRepository", [],
        ["+ findGrantedPermissions(Collection<Long>, String): List<...>",
         "+ findByRoleId(Long): List<IamRolePermission>",
         "+ save(IamRolePermission): IamRolePermission"],
        "«interface»", "interface",
    )

    d.association(
        [(330, 322), (330, 470), (820, 470), (820, 570)],
        "decisionService", (585, 458), "interceptor", "decision",
    )
    d.association([(1020, 417), (1020, 570)], "decisionService",
                  (1080, 500), "identity", "decision")
    d.association(
        [(1730, 301), (1730, 500), (1460, 500), (1460, 570)],
        "decisionService", (1600, 488), "open", "decision",
    )
    d.association([(1690, 750), (1460, 750)], "decisionService",
                  (1575, 737), "role_service", "decision")
    d.association(
        [(880, 845), (880, 1030), (300, 1030), (300, 1235)],
        "userRepository", (570, 1018), "decision", "user_repo",
    )
    d.association(
        [(980, 845), (980, 1110), (820, 1110), (820, 1235)],
        "userRoleRepository", (850, 1098), "decision", "user_role_repo",
    )
    d.association(
        [(1080, 845), (1080, 1150), (1360, 1150), (1360, 1235)],
        "roleRepository", (1250, 1138), "decision", "role_repo",
    )
    d.association(
        [(1260, 845), (1260, 1040), (1960, 1040), (1960, 1235)],
        "rolePermissionRepository", (1710, 1028), "decision", "role_perm_repo",
    )
    d.footer = (
        "判定链：用户状态 → 有效期内的用户角色 → 启用角色及父角色 → "
        "角色权限候选（系统/操作过滤）→ 权限 ID 集合。"
    )
    return d


def oop_auth_class() -> ClassDiagram:
    d = ClassDiagram(
        2100,
        1300,
        "面向对象后端 · 认证用例类图",
        "以 AuthenticationUseCase 为中心；应用层只依赖端口，不直接依赖 JPA 或 JWT 实现。",
    )
    d.add_box(
        "controller", 100, 120, 530, "AuthController",
        ["- authenticationUseCase: AuthenticationUseCase",
         "- operationAccessUseCase: OperationAccessUseCase"],
        ["+ login(LoginRequestDTO, HttpServletRequest): LoginResult",
         "+ logout(String): void", "+ apiPermission(...): ApiPermissionCheckVO"],
        "«controller»", "caller",
    )
    d.add_box(
        "interceptor", 1450, 115, 540, "AuthInterceptor",
        ["- authenticationUseCase: AuthenticationUseCase",
         "- operationAccessUseCase: OperationAccessUseCase",
         "- auditTrail: AuditTrail"],
        ["+ preHandle(...): boolean", "+ afterCompletion(...): void"],
        "«component»", "caller",
    )
    d.add_box(
        "auth", 690, 470, 720, "AuthenticationUseCase",
        ["- userRepository: UserAccountRepository",
         "- tokenRepository: TokenSessionRepository",
         "- passwordVerifier: PasswordVerifier", "- nameCipher: NameCipher",
         "- tokenSecretHasher: TokenSecretHasher", "- tokenIssuer: AccessTokenIssuer",
         "- auditTrail: AuditTrail", "- clock: Clock"],
        ["+ login(LoginCommand): LoginResult", "+ authenticate(String): AuthenticatedPrincipal",
         "+ logout(String): void", "+ validate(String): boolean", "+ userNo(Long): String"],
        "«service»", "service",
    )
    d.add_box(
        "password", 100, 500, 430, "PasswordVerifier", [],
        ["+ verify(String, String): boolean"], "«interface»", "interface",
    )
    d.add_box(
        "name", 100, 735, 430, "NameCipher", [],
        ["+ decrypt(String): String"], "«interface»", "interface",
    )
    d.add_box(
        "issuer", 1510, 490, 460, "AccessTokenIssuer", [],
        ["+ issue(String, List<String>): IssuedToken",
         "+ read(String): AccessTokenClaims"], "«interface»", "interface",
    )
    d.add_box(
        "hasher", 1510, 710, 460, "TokenSecretHasher", [],
        ["+ hash(String): String"], "«interface»", "interface",
    )
    d.add_box(
        "user_repo", 160, 1000, 430, "UserAccountRepository", [],
        ["+ findById(Long): Optional<UserAccount>",
         "+ findByUserNo(String): Optional<UserAccount>",
         "+ save(UserAccount): UserAccount"], "«interface»", "interface",
    )
    d.add_box(
        "token_repo", 650, 1000, 430, "TokenSessionRepository", [],
        ["+ findByTokenHash(String): Optional<TokenSession>",
         "+ save(TokenSession): void"], "«interface»", "interface",
    )
    d.add_box(
        "audit", 1160, 1000, 430, "AuditTrail", [],
        ["+ recordLogin(...): void", "+ recordRequest(...): void",
         "+ recordPermissionChange(...): void"], "«interface»", "interface",
    )
    d.add_box(
        "clock", 1650, 1000, 350, "Clock", [],
        ["+ instant(): Instant", "+ millis(): long"], "«JDK»", "enum",
    )

    d.association(
        [(350, 311), (350, 390), (760, 390), (760, 470)],
        "authenticationUseCase", (555, 378), "controller", "auth",
    )
    d.association(
        [(1720, 306), (1720, 390), (1340, 390), (1340, 470)],
        "authenticationUseCase", (1530, 378), "interceptor", "auth",
    )
    d.association([(690, 550), (530, 550)], "passwordVerifier",
                  (610, 537), "auth", "password")
    d.association([(690, 795), (530, 795)], "nameCipher",
                  (610, 782), "auth", "name")
    d.association([(1410, 550), (1510, 550)], "tokenIssuer",
                  (1460, 537), "auth", "issuer")
    d.association([(1410, 765), (1510, 765)], "tokenSecretHasher",
                  (1460, 752), "auth", "hasher")
    d.association(
        [(800, 829), (800, 915), (375, 915), (375, 1000)],
        "userRepository", (580, 903), "auth", "user_repo",
    )
    d.association(
        [(970, 829), (970, 935), (865, 935), (865, 1000)],
        "tokenRepository", (915, 923), "auth", "token_repo",
    )
    d.association(
        [(1120, 829), (1120, 920), (1375, 920), (1375, 1000)],
        "auditTrail", (1245, 908), "auth", "audit",
    )
    d.association(
        [(1300, 829), (1300, 945), (1825, 945), (1825, 1000)],
        "clock", (1560, 933), "auth", "clock",
    )
    d.footer = (
        "右侧与下方为应用端口。具体实现位于 infrastructure："
        "JwtAccessTokenIssuer、Jpa*RepositoryAdapter、Pbkdf2PasswordVerifier、AesNameCipher 等。"
    )
    return d


def oop_permission_class() -> ClassDiagram:
    d = ClassDiagram(
        2400,
        1510,
        "面向对象后端 · 权限查询类图",
        "以 PermissionAccessUseCase 为中心；仓储只返回授权事实，PermissionResolver 负责纯计算。",
    )
    d.add_box(
        "operation", 880, 100, 640, "OperationAccessUseCase",
        ["- operationCatalogRepository: OperationCatalogRepository",
         "- permissionAccessUseCase: PermissionAccessUseCase"],
        ["+ requireRequest(Long, String, String, String): void",
         "+ operationAllowed(Long, String, String): boolean"],
        "«service»", "caller",
    )
    d.add_box(
        "role_grant_repo", 100, 500, 450, "RoleGrantRepository", [],
        ["+ findByUserId(Long): List<RoleGrant>",
         "+ findByUserIdAndRoleId(Long, Long): Optional<RoleGrant>",
         "+ save(RoleGrant): RoleGrant"], "«interface»", "interface",
    )
    d.add_box(
        "role_repo", 100, 760, 450, "RoleRepository", [],
        ["+ findById(Long): Optional<Role>", "+ findAll(): List<Role>",
         "+ save(Role): Role"], "«interface»", "interface",
    )
    d.add_box(
        "catalog_repo", 100, 1040, 450, "PermissionCatalogRepository", [],
        ["+ findFact(Long): Optional<PermissionFact>",
         "+ findFacts(Collection<Long>): List<PermissionFact>"],
        "«interface»", "interface",
    )
    d.add_box(
        "user_repo", 1830, 500, 500, "UserAccountRepository", [],
        ["+ findById(Long): Optional<UserAccount>",
         "+ findByUserNo(String): Optional<UserAccount>",
         "+ save(UserAccount): UserAccount"], "«interface»", "interface",
    )
    d.add_box(
        "role_perm_repo", 1830, 760, 500, "RolePermissionRepository", [],
        ["+ findByRoleId(Long): List<RolePermissionGrant>",
         "+ findByRoleIds(Collection<Long>): List<RolePermissionGrant>",
         "+ bind(...): void"], "«interface»", "interface",
    )
    d.add_box(
        "clock", 1900, 1040, 400, "Clock", [],
        ["+ instant(): Instant"], "«JDK»", "enum",
    )
    d.add_box(
        "permission", 800, 480, 700, "PermissionAccessUseCase",
        ["- userRepository: UserAccountRepository",
         "- roleGrantRepository: RoleGrantRepository",
         "- roleRepository: RoleRepository",
         "- rolePermissionRepository: RolePermissionRepository",
         "- permissionCatalogRepository: PermissionCatalogRepository",
         "- clock: Clock"],
        ["+ permissions(Long, String): Set<Long>",
         "+ hasPermission(Long, String, Long): boolean"],
        "«service»", "service",
    )
    d.add_box(
        "resolver", 800, 1120, 520, "PermissionResolver", [],
        ["+ resolve(List<RoleGrant>, Map<Long, Role>, ...): Set<Long>"],
        "«utility service»", "domain",
    )
    d.add_box(
        "hierarchy", 1380, 1120, 480, "RoleHierarchy", [],
        ["+ activeRoleIds(Set<Long>, Function<...>): Set<Long>",
         "+ ensureNoCycle(Long, Long, Function<...>): void"],
        "«utility service»", "domain",
    )

    d.association([(1200, 324), (1200, 480)], "permissionAccessUseCase",
                  (1330, 410), "operation", "permission")
    d.association([(800, 570), (550, 570)], "roleGrantRepository",
                  (675, 557), "permission", "role_grant_repo")
    d.association(
        [(800, 650), (620, 650), (620, 760), (550, 760)],
        "roleRepository", (660, 712), "permission", "role_repo",
    )
    d.association([(1500, 580), (1830, 580)], "userRepository",
                  (1665, 567), "permission", "user_repo")
    d.association([(1500, 690), (1830, 690)], "rolePermissionRepository",
                  (1665, 677), "permission", "role_perm_repo")
    d.association(
        [(900, 734), (900, 930), (520, 930), (520, 1040)],
        "permissionCatalogRepository", (680, 918), "permission", "catalog_repo",
    )
    d.association(
        [(1350, 734), (1350, 960), (2040, 960), (2040, 1040)],
        "clock", (1695, 948), "permission", "clock",
    )
    d.dependency([(1100, 734), (1100, 1120)], "permission", "resolver")
    d.dependency([(1320, 1190), (1380, 1190)], "resolver", "hierarchy")
    d.footer = (
        "PermissionResolver 与 RoleHierarchy 仅接收已读取的事实并做静态纯计算；"
        "数据库、映射和时钟注入均通过端口隔离。"
    )
    return d


def structured_login_sequence() -> SequenceDiagram:
    s = SequenceDiagram(
        3040,
        1900,
        "结构化后端 · 登录成功与凭证失败",
        "alt 覆盖正常登录与密码校验失败；异常从 AuthService 执行条直接返回控制器执行条。",
    )
    s.lifeline("ui", "web", "LoginPage", 180)
    s.lifeline("controller", "controller", "IdentityAuthController", 560)
    s.lifeline("service", "authService", "AuthService", 960)
    s.lifeline("user_repo", "userRepository", "IamUserRepository", 1360)
    s.lifeline("crypto", "cryptoComponent", "CryptoComponent", 1720)
    s.lifeline("token_repo", "tokenRepository", "IamTokenRepository", 2080)
    s.lifeline("log", "logService", "LogService", 2460)

    ui_act = s.activation("ui", 185, 1810)
    controller_act = s.activation("controller", 185, 1810)
    service_act = s.activation("service", 250, 1720)
    s.sync("ui", "controller", 185, 1810, "login(username, password, systems)",
           ui_act, controller_act, True)
    s.sync("controller", "service", 250, 1710, "authService.login(request)",
           controller_act, service_act, True)

    s.fragment(
        "alt", "登录校验",
        500, 2690, 260, 1805,
        [ ],
        [ui_act, controller_act, service_act],
    )
    s.guard(500, 2690, 325, "[账号正常且密码正确]")

    repo_ok = s.activation("user_repo", 360, 470)
    s.sync("service", "user_repo", 360, 430,
           "findByUserNo(username)", service_act, repo_ok)
    s.reply("user_repo", "service", 470, "user: IamUser", repo_ok, service_act)
    crypto_ok = s.activation("crypto", 520, 630)
    s.sync("service", "crypto", 520, 590,
           "verifyPassword(password, user.passwordHash)", service_act, crypto_ok)
    s.reply("crypto", "service", 630, "true", crypto_ok, service_act)
    hash_act = s.activation("crypto", 680, 780)
    s.sync("service", "crypto", 680, 740,
           "hash(accessToken)", service_act, hash_act)
    s.reply("crypto", "service", 780, "tokenHash", hash_act, service_act)
    token_save = s.activation("token_repo", 830, 900)
    s.sync("service", "token_repo", 830, 900,
           "save(IamToken)", service_act, token_save)
    user_save = s.activation("user_repo", 950, 1010)
    s.sync("service", "user_repo", 950, 1010,
           "save(user with lastLoginAt)", service_act, user_save)
    log_ok = s.activation("log", 1060, 1120)
    s.sync("service", "log", 1060, 1120,
           "record(..., LOGIN, true, ...)", service_act, log_ok)
    s.reply("service", "controller", 1170, "LoginResponse", service_act, controller_act)
    s.reply("controller", "ui", 1220, "200 LoginResponse", controller_act, ui_act)

    s.guard(500, 2690, 1260, "[账号不存在或密码错误]")
    s.next_number = 3
    repo_fail = s.activation("user_repo", 1300, 1410)
    s.sync("service", "user_repo", 1300, 1370,
           "findByUserNo(username)", service_act, repo_fail)
    s.reply("user_repo", "service", 1410, "user: IamUser", repo_fail, service_act)
    crypto_fail = s.activation("crypto", 1460, 1570)
    s.sync("service", "crypto", 1460, 1530,
           "verifyPassword(password, user.passwordHash)", service_act, crypto_fail)
    s.reply("crypto", "service", 1570, "false", crypto_fail, service_act)
    log_fail = s.activation("log", 1600, 1660)
    s.sync("service", "log", 1600, 1660,
           "record(..., LOGIN, false, ...)", service_act, log_fail)
    s.reply("service", "controller", 1710,
            "BusinessException(UNAUTHENTICATED)", service_act, controller_act,
            exception=True)
    s.reply("controller", "ui", 1770, "401 账号或密码错误",
            controller_act, ui_act)
    return s


def structured_management_auth_sequence() -> SequenceDiagram:
    s = SequenceDiagram(
        4300,
        1640,
        "结构化后端 · 管理端默认拒绝鉴权",
        "公共认证与 API 映射查询在 alt 外；成功分支展开 READ 判定与父角色循环，失败分支直接抛 403 语义异常。",
    )
    s.lifeline("browser", "browser", "管理端页面", 180)
    s.lifeline("interceptor", "authInterceptor", "AuthInterceptor", 600)
    s.lifeline("auth", "authService", "AuthService", 1050)
    s.lifeline("api", "apiService", "ApiService", 1460)
    s.lifeline("decision", "decisionService", "DecisionService", 1920)
    s.lifeline("user_repo", "userRepository", "IamUserRepository", 2400)
    s.lifeline("grant_repo", "userRoleRepository", "IamUserRoleRepository", 2850)
    s.lifeline("role_repo", "roleRepository", "IamRoleRepository", 3300)
    s.lifeline("perm_repo", "rolePermissionRepository", "IamRolePermissionRepository", 3750)

    browser_act = s.activation("browser", 180, 1530)
    interceptor_act = s.activation("interceptor", 180, 1530)
    s.sync("browser", "interceptor", 180, 1530,
           "preHandle(GET /roles?page=1)", browser_act, interceptor_act)
    auth_act = s.activation("auth", 250, 390)
    s.sync("interceptor", "auth", 250, 390,
           "authenticateSession(token)", interceptor_act, auth_act)
    s.reply("auth", "interceptor", 390,
            "session(userId, systemCode)", auth_act, interceptor_act)
    api_act = s.activation("api", 440, 570)
    s.sync("interceptor", "api", 440, 570,
           "findByRequest(pattern, GET)", interceptor_act, api_act)
    s.reply("api", "interceptor", 570, "IamApi(permissionId)", api_act, interceptor_act)

    s.fragment(
        "alt", "默认拒绝判定",
        520, 4180, 600, 1525,
        [], [browser_act, interceptor_act],
    )
    s.guard(520, 4180, 645, "[API 已登记且功能权限通过]")
    decision_act = s.activation("decision", 690, 1420)
    s.sync("interceptor", "decision", 690, 1230,
           "hasPermission(userId, ADMIN, permissionId)",
           interceptor_act, decision_act)
    user_act = s.activation("user_repo", 750, 850)
    s.sync("decision", "user_repo", 750, 810,
           "findById(userId)", decision_act, user_act)
    s.reply("user_repo", "decision", 850, "user(status=NORMAL)", user_act, decision_act)
    grant_act = s.activation("grant_repo", 900, 1000)
    s.sync("decision", "grant_repo", 900, 960,
           "findByUserIdAndStatus(userId, ACTIVE)", decision_act, grant_act)
    s.reply("grant_repo", "decision", 1000,
            "grants within effectiveTime", grant_act, decision_act)

    s.fragment(
        "loop", "逐层展开父角色",
        1880, 3710, 1010, 1180,
        [], [decision_act],
    )
    role_act = s.activation("role_repo", 1060, 1150)
    s.sync("decision", "role_repo", 1060, 1125,
           "findAllById(toVisit)", decision_act, role_act)
    s.reply("role_repo", "decision", 1150,
            "active roles, next=parentId", role_act, decision_act)
    s.note(3210, 1075, 300,
           ["每层创建独立执行条", "visited 防止继承成环"])

    perm_act = s.activation("perm_repo", 1210, 1320)
    s.sync("decision", "perm_repo", 1210, 1280,
           "findGrantedPermissions(roleIds, systemCode)", decision_act, perm_act)
    s.reply("perm_repo", "decision", 1320,
            "GrantedPermissionView[]", perm_act, decision_act)
    s.note(2050, 1350, 650,
           ["内存过滤：allowedAction 覆盖 requiredAction",
            "去重后返回 Set<permissionId>"])
    s.reply("decision", "interceptor", 1420, "true", decision_act, interceptor_act)
    s.note(760, 1450, 520,
           ["preHandle 返回 true",
            "后续由 Spring MVC 调用目标控制器"])

    s.guard(520, 4180, 1485, "[API 未登记或无功能权限]")
    s.reply("interceptor", "browser", 1500,
            "BusinessException(NO_FUNCTION_PERM) → 403",
            interceptor_act, browser_act, exception=True)
    return s


def structured_role_permission_sequence() -> SequenceDiagram:
    s = SequenceDiagram(
        3200,
        1420,
        "结构化后端 · 角色前端权限授予",
        "role：循环前读取角色与已有绑定；loop 内逐项校验并保存；循环后重新读取快照并记录审计。",
    )
    s.lifeline("client", "bindPanel", "RolePermissionBindCard", 220)
    s.lifeline("controller", "roleController", "RoleController", 700)
    s.lifeline("service", "roleService", "RoleService", 1120)
    s.lifeline("role_repo", "roleRepository", "IamRoleRepository", 1540)
    s.lifeline("binding_repo", "rolePermissionRepository", "IamRolePermissionRepository", 2010)
    s.lifeline("perm_repo", "permissionRepository", "IamPermissionRepository", 2470)
    s.lifeline("log", "logService", "LogService", 2900)

    client_act = s.activation("client", 180, 1320)
    controller_act = s.activation("controller", 180, 1320)
    service_act = s.activation("service", 250, 1250)
    s.sync("client", "controller", 180, 1320,
           "bindPermissions(roleId, permissionIds, allowedAction)",
           client_act, controller_act)
    s.sync("controller", "service", 250, 1250,
           "bindRolePermissions(...)", controller_act, service_act)
    role_act = s.activation("role_repo", 310, 410)
    s.sync("service", "role_repo", 310, 370,
           "findById(roleId)", service_act, role_act)
    s.reply("role_repo", "service", 410, "IamRole(ACTIVE)", role_act, service_act)
    bound_act = s.activation("binding_repo", 460, 560)
    s.sync("service", "binding_repo", 460, 560,
           "findByRoleId(roleId)", service_act, bound_act)
    s.reply("binding_repo", "service", 560,
            "existing bindings", bound_act, service_act)

    s.fragment(
        "loop", "对每个 permissionId",
        1880, 2690, 600, 930,
        [], [service_act, controller_act, client_act],
    )
    perm_act = s.activation("perm_repo", 650, 750)
    s.sync("service", "perm_repo", 650, 710,
           "findById(permissionId)", service_act, perm_act)
    s.reply("perm_repo", "service", 750,
            "IamPermission(ACTIVE)", perm_act, service_act)
    save_act = s.activation("binding_repo", 800, 860)
    s.sync("service", "binding_repo", 800, 860,
           "save(new IamRolePermission)", service_act, save_act)
    s.note(2260, 885, 360,
           ["已绑定 ID 跳过", "每轮执行条独立"])

    current_act = s.activation("binding_repo", 970, 1075)
    s.sync("service", "binding_repo", 970, 1035,
           "findByRoleId(roleId)", service_act, current_act)
    s.reply("binding_repo", "service", 1075,
            "after permission IDs", current_act, service_act)
    log_act = s.activation("log", 1110, 1170)
    s.sync("service", "log", 1110, 1170,
           "recordPermissionChange(createdBy, ...)", service_act, log_act)
    s.reply("service", "controller", 1210,
            "void", service_act, controller_act)
    s.reply("controller", "client", 1260,
            "200 Result.success()", controller_act, client_act)
    return s


def oop_permission_sequence() -> SequenceDiagram:
    s = SequenceDiagram(
        4400,
        1420,
        "面向对象后端 · 按 operationId 查询权限",
        "AuthController → OperationAccessUseCase → PermissionAccessUseCase；静态纯计算以 MetaClass lifeline 表示。",
    )
    s.lifeline("controller", "authController", "AuthController", 180)
    s.lifeline("operation", "operationAccessUseCase", "OperationAccessUseCase", 690)
    s.lifeline("permission", "permissionAccessUseCase", "PermissionAccessUseCase", 1210)
    s.lifeline("user_repo", "userRepository", "UserAccountRepository", 1680)
    s.lifeline("grant_repo", "roleGrantRepository", "RoleGrantRepository", 2060)
    s.lifeline("role_repo", "roleRepository", "RoleRepository", 2420)
    s.lifeline("perm_repo", "rolePermissionRepository", "RolePermissionRepository", 2780)
    s.lifeline("catalog_repo", "permissionCatalogRepository", "PermissionCatalogRepository", 3250)
    s.lifeline("resolver", "permissionResolver", "PermissionResolver", 3690, meta=True)
    s.lifeline("hierarchy", "roleHierarchy", "RoleHierarchy", 4090, meta=True)

    controller_act = s.activation("controller", 180, 1330)
    operation_act = s.activation("operation", 180, 1330)
    permission_act = s.activation("permission", 260, 1290)
    s.sync("controller", "operation", 180, 1330,
           "operationAllowed(userId, systemCode, operationId)",
           controller_act, operation_act)
    s.sync("operation", "permission", 260, 1290,
           "hasPermission(userId, systemCode, permissionId)",
           operation_act, permission_act)
    user_act = s.activation("user_repo", 330, 430)
    s.sync("permission", "user_repo", 330, 430,
           "findById(userId)", permission_act, user_act)
    s.reply("user_repo", "permission", 430,
            "UserAccount(canAuthenticate=true)", user_act, permission_act)
    grant_act = s.activation("grant_repo", 480, 580)
    s.sync("permission", "grant_repo", 480, 580,
           "findByUserId(userId)", permission_act, grant_act)
    s.reply("grant_repo", "permission", 580, "grants", grant_act, permission_act)
    roles_act = s.activation("role_repo", 630, 730)
    s.sync("permission", "role_repo", 630, 730,
           "findAll()", permission_act, roles_act)
    s.reply("role_repo", "permission", 730, "Map<roleId, Role>", roles_act, permission_act)
    grants_act = s.activation("perm_repo", 780, 880)
    s.sync("permission", "perm_repo", 780, 880,
           "findByRoleIds(roleIds)", permission_act, grants_act)
    s.reply("perm_repo", "permission", 880,
            "rolePermissionGrants", grants_act, permission_act)
    facts_act = s.activation("catalog_repo", 930, 1030)
    s.sync("permission", "catalog_repo", 930, 1030,
           "findFacts(permissionIds)", permission_act, facts_act)
    s.reply("catalog_repo", "permission", 1030,
            "permissionFacts", facts_act, permission_act)

    resolver_act = s.activation("resolver", 1070, 1250)
    s.sync("permission", "resolver", 1070, 1250,
           "PermissionResolver.resolve(grants, roles, ...)", permission_act, resolver_act)
    hierarchy_act = s.activation("hierarchy", 1110, 1210)
    s.sync("resolver", "hierarchy", 1110, 1210,
           "RoleHierarchy.activeRoleIds(directRoleIds, lookup)", resolver_act, hierarchy_act)
    s.reply("hierarchy", "resolver", 1200,
            "effectiveRoleIds", hierarchy_act, resolver_act)
    s.note(3650, 1210, 470,
           ["纯静态计算", "过滤有效授权、系统、启用状态与操作覆盖"])
    s.reply("resolver", "permission", 1240, "Set<Long>", resolver_act, permission_act)
    s.reply("permission", "operation", 1280,
            "true", permission_act, operation_act)
    s.reply("operation", "controller", 1310,
            "ApiPermissionCheckVO(operationId, true)",
            operation_act, controller_act)
    return s


def build() -> list[Path]:
    OUT.mkdir(parents=True, exist_ok=True)
    generated: list[Path] = []

    class_specs = [
        (structured_auth_class(), "类图_结构化后端_认证.svg", "类图_结构化后端_认证.png"),
        (structured_decision_class(), "类图_结构化后端_权限决策.svg", "类图_结构化后端_权限决策.png"),
        (oop_auth_class(), "类图_面向对象后端_认证.svg", "类图_面向对象后端_认证.png"),
        (oop_permission_class(), "类图_面向对象后端_权限决策.svg", "类图_面向对象后端_权限决策.png"),
    ]
    sequence_specs = [
        (structured_login_sequence(), "顺序图_结构化后端_登录.svg", "顺序图_结构化后端_登录.png"),
        (structured_management_auth_sequence(),
         "顺序图_结构化后端_管理端鉴权.svg", "顺序图_结构化后端_管理端鉴权.png"),
        (structured_role_permission_sequence(),
         "顺序图_结构化后端_角色权限授予.svg", "顺序图_结构化后端_角色权限授予.png"),
        (oop_permission_sequence(),
         "顺序图_面向对象后端_权限查询.svg", "顺序图_面向对象后端_权限查询.png"),
    ]

    for diagram, svg_name, png_name in class_specs + sequence_specs:
        svg = OUT / svg_name
        png = OUT / png_name
        diagram.save(svg)
        render_png(svg, png)
        generated.extend([svg, png])
        print(f"generated {svg.relative_to(ROOT)}")
        print(f"generated {png.relative_to(ROOT)}")
    return generated


if __name__ == "__main__":
    try:
        build()
    except Exception as exc:
        print(f"UML build failed: {exc}", file=sys.stderr)
        raise

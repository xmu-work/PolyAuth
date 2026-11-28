"""Class diagrams for the seven paired business functions.

Each function has a structured-backend and an object-oriented-backend page.
The diagrams are built with :class:`uml_layout.ClassScene`, which keeps the
target service in the geometric centre and routes every edge orthogonally
around the other class boxes.
"""
from __future__ import annotations

from uml_layout import ClassScene


# ---------------------------------------------------------------------------
# 1. 角色管理与授权
# ---------------------------------------------------------------------------

def structured_role_class():
    d = ClassScene(
        "结构化后端 · 角色授权类图",
        "以 RoleService 为中心：控制器在上方，决策与日志服务在两侧，六个仓储在下方。",
        "授权先加用户+角色粒度分布式锁；角色权限授予逐项校验并记录权限变更快照。",
    )
    d.service(
        "role_service", "RoleService",
        ["- roleRepository: IamRoleRepository",
         "- userRepository: IamUserRepository",
         "- userRoleRepository: IamUserRoleRepository",
         "- permissionRepository: IamPermissionRepository",
         "- rolePermissionRepository: IamRolePermissionRepository",
         "- roleExclusionRepository: IamRoleExclusionRepository",
         "- decisionService: DecisionService",
         "- logService: LogService",
         "- lockExecutor: BusinessLockExecutor"],
        ["+ createRole(String, String, String, String): IamRole",
         "+ updateRole(Long, String, String, String, Long): IamRole",
         "+ grantRole(Long, Long, LocalDateTime, Long, String): IamUserRole",
         "+ revokeRole(Long, Long, Long): void",
         "+ bindRolePermissions(Long, List<Long>, String, Long): void",
         "+ bindRoleExclusion(Long, Long, Long): void",
         "- checkGrantBoundary(Long, Long): void"],
    )
    d.node(
        "controller", "RoleController",
        ["- roleService: RoleService"],
        ["+ create(...): Result<RoleVO>",
         "+ update(...): Result<RoleVO>",
         "+ assignUser(...): Result<UserRoleVO>",
         "+ bindPermissions(...): Result<Void>",
         "+ bindExclusion(...): Result<Void>"],
        "top", "«controller»", "caller", "service", "roleService",
    )
    d.node(
        "decision", "DecisionService",
        ["- userRepository: IamUserRepository",
         "- userRoleRepository: IamUserRoleRepository",
         "- roleRepository: IamRoleRepository",
         "- rolePermissionRepository: IamRolePermissionRepository"],
        ["+ hasPermission(Long, String, Long): boolean",
         "+ getUserPermissions(Long, String): Set<Long>"],
        "left", "«service»", "caller", "node", "decisionService", order=0,
    )
    d.node(
        "log", "LogService",
        ["- logRepository: IamLogRepository"],
        ["+ recordPermissionChange(Long, ...): void"],
        "left", "«service»", "infra", "node", "logService", order=1,
    )
    d.node(
        "lock", "BusinessLockExecutor",
        ["- permissionCache: PermissionCacheComponent"],
        ["+ execute(String, Supplier<T>): T"],
        "right", "«component»", "infra", "node", "lockExecutor",
    )
    d.node(
        "role_repo", "IamRoleRepository", [],
        ["+ findById(Long): Optional<IamRole>",
         "+ findByRoleCode(String): Optional<IamRole>",
         "+ findAllById(Iterable<Long>): List<IamRole>",
         "+ save(IamRole): IamRole"],
        "bottom", "«interface · Spring Data JPA»", "interface", "node",
        "roleRepository", order=0,
    )
    d.node(
        "user_repo", "IamUserRepository", [],
        ["+ findById(Long): Optional<IamUser>",
         "+ findByUserNo(String): Optional<IamUser>",
         "+ save(IamUser): IamUser"],
        "bottom", "«interface · Spring Data JPA»", "interface", "node",
        "userRepository", order=1,
    )
    d.node(
        "grant_repo", "IamUserRoleRepository", [],
        ["+ findByUserIdAndRoleId(Long, Long): Optional<IamUserRole>",
         "+ findByUserIdAndStatus(Long, String): List<IamUserRole>",
         "+ save(IamUserRole): IamUserRole"],
        "bottom", "«interface · Spring Data JPA»", "interface", "node",
        "userRoleRepository", order=2,
    )
    d.node(
        "permission_repo", "IamPermissionRepository", [],
        ["+ findById(Long): Optional<IamPermission>",
         "+ findBySystemIdAndPermissionCode(...): Optional<IamPermission>"],
        "bottom", "«interface · Spring Data JPA»", "interface", "node",
        "permissionRepository", order=3,
    )
    d.node(
        "binding_repo", "IamRolePermissionRepository", [],
        ["+ findByRoleId(Long): List<IamRolePermission>",
         "+ findGrantedPermissions(...): List<GrantedPermissionView>",
         "+ save(IamRolePermission): IamRolePermission"],
        "bottom", "«interface · Spring Data JPA»", "interface", "node",
        "rolePermissionRepository", order=4,
    )
    d.node(
        "exclusion_repo", "IamRoleExclusionRepository", [],
        ["+ findByRoleIdOrExcludedRoleId(Long, Long): List<IamRoleExclusion>",
         "+ findByRoleIdAndExcludedRoleId(...): Optional<IamRoleExclusion>"],
        "bottom", "«interface · Spring Data JPA»", "interface", "node",
        "roleExclusionRepository", order=5,
    )
    return d.build()


def oop_role_class():
    d = ClassScene(
        "面向对象后端 · 角色授权用例类图",
        "以 RoleManagementUseCase 为中心：授权事实来自领域仓储，互斥与继承规则由领域服务承担。",
        "RoleHierarchy.ensureNoCycle 在修改父角色时静态执行；所有时间来自注入的 Clock。",
    )
    d.service(
        "role_uc", "RoleManagementUseCase",
        ["- roleRepository: RoleRepository",
         "- userRepository: UserAccountRepository",
         "- roleGrantRepository: RoleGrantRepository",
         "- rolePermissionRepository: RolePermissionRepository",
         "- permissionCatalogRepository: PermissionCatalogRepository",
         "- roleExclusionRepository: RoleExclusionRepository",
         "- permissionAccessUseCase: PermissionAccessUseCase",
         "- auditTrail: AuditTrail",
         "- clock: Clock"],
        ["+ createRole(CreateRoleCommand): Role",
         "+ updateRole(UpdateRoleCommand): Role",
         "+ grantRole(GrantRoleCommand): RoleGrant",
         "+ revokeRole(Long, Long, Long): void",
         "+ bindPermissions(Long, List<Long>, String, Long): void",
         "+ bindExclusion(Long, Long, Long): void",
         "- checkGrantBoundary(Long, Long): void"],
    )
    d.node(
        "controller", "RoleController",
        ["- roleManagementUseCase: RoleManagementUseCase"],
        ["+ create(...): ApiResult<RoleVO>",
         "+ update(...): ApiResult<RoleVO>",
         "+ assignUser(...): ApiResult<UserRoleVO>",
         "+ bindPermissions(...): ApiResult<Void>",
         "+ bindExclusion(...): ApiResult<Void>"],
        "top", "«controller»", "caller", "service", "roleManagementUseCase",
    )
    d.node(
        "permission_uc", "PermissionAccessUseCase",
        ["- userRepository: UserAccountRepository",
         "- roleGrantRepository: RoleGrantRepository"],
        ["+ permissions(Long, String): Set<Long>",
         "+ hasPermission(Long, String, Long): boolean"],
        "left", "«service»", "caller", "node", "permissionAccessUseCase", order=0,
    )
    d.node(
        "audit", "AuditTrail",
        [],
        ["+ recordPermissionChange(Long, String, String, String, Object, Object): void"],
        "left", "«interface · port»", "interface", "node", "auditTrail", order=1,
    )
    d.node(
        "clock", "Clock", [],
        ["+ instant(): Instant", "+ getZone(): ZoneId"],
        "right", "«JDK»", "enum", "node", "clock", order=0,
    )
    d.node(
        "role_repo", "RoleRepository", [],
        ["+ findById(Long): Optional<Role>",
         "+ findByCode(String): Optional<Role>",
         "+ findAll(): List<Role>",
         "+ save(Role): Role"],
        "bottom", "«interface · domain»", "interface", "node",
        "roleRepository", order=0,
    )
    d.node(
        "user_repo", "UserAccountRepository", [],
        ["+ findById(Long): Optional<UserAccount>",
         "+ findByUserNo(String): Optional<UserAccount>",
         "+ save(UserAccount): UserAccount"],
        "bottom", "«interface · domain»", "interface", "node",
        "userRepository", order=1,
    )
    d.node(
        "grant_repo", "RoleGrantRepository", [],
        ["+ findByUserIdAndRoleId(Long, Long): Optional<RoleGrant>",
         "+ findByUserId(Long): List<RoleGrant>",
         "+ findByRoleId(Long): List<RoleGrant>",
         "+ save(RoleGrant): RoleGrant"],
        "bottom", "«interface · domain»", "interface", "node",
        "roleGrantRepository", order=2,
    )
    d.node(
        "binding_repo", "RolePermissionRepository", [],
        ["+ findByRoleId(Long): List<RolePermissionGrant>",
         "+ bind(Long, Long, OperationAction, Long): void",
         "+ unbind(Long, Long): void"],
        "bottom", "«interface · domain»", "interface", "node",
        "rolePermissionRepository", order=3,
    )
    d.node(
        "catalog_repo", "PermissionCatalogRepository", [],
        ["+ findFact(Long): Optional<PermissionFact>",
         "+ findFacts(Collection<Long>): List<PermissionFact>"],
        "bottom", "«interface · domain»", "interface", "node",
        "permissionCatalogRepository", order=4,
    )
    d.node(
        "exclusion_repo", "RoleExclusionRepository", [],
        ["+ findPair(Long, Long): Optional<RoleExclusion>",
         "+ findByRole(Long): List<RoleExclusion>",
         "+ save(RoleExclusion): RoleExclusion"],
        "bottom", "«interface · domain»", "interface", "node",
        "roleExclusionRepository", order=5,
    )
    d.node(
        "hierarchy", "RoleHierarchy", [],
        ["+ activeRoleIds(Set<Long>, Function): Set<Long>",
         "+ ensureNoCycle(Long, Long, Function): void"],
        "bottom", "«domain service»", "domain", "node", order=6,
        kind="dependency",
    )
    return d.build()


# ---------------------------------------------------------------------------
# 2. 账号管理
# ---------------------------------------------------------------------------

def structured_account_class():
    d = ClassScene(
        "结构化后端 · 账号管理类图",
        "以 AccountService 为中心：状态变更会联动 AuthService 吊销令牌、RoleService 回收角色。",
        "创建的密码与姓名分别经 CryptoComponent 哈希、加密；调岗只改组织与岗位并保留已有角色。",
    )
    d.service(
        "account", "AccountService",
        ["- userRepository: IamUserRepository",
         "- cryptoComponent: CryptoComponent",
         "- authService: AuthService",
         "- roleService: RoleService",
         "- orgRepository: IamOrgRepository",
         "- logService: LogService"],
        ["+ createUser(String, String, String, String, Long, String): IamUser",
         "+ listUsers(Pageable, Long, String): Page<IamUser>",
         "+ getUser(Long): IamUser",
         "+ changeStatus(Long, String, String, Long): IamUser",
         "+ transferUser(Long, Long, String, Long): IamUser",
         "+ toView(IamUser): UserView",
         "+ getSensitiveView(Long): SensitiveUserView"],
    )
    d.node(
        "controller", "UserController",
        ["- accountService: AccountService"],
        ["+ create(...): Result<UserVO>",
         "+ list(...): Result<PageResult<UserVO>>",
         "+ changeStatus(...): Result<UserVO>",
         "+ transfer(...): Result<UserVO>"],
        "top", "«controller»", "caller", "service", "accountService",
    )
    d.node(
        "auth", "AuthService",
        ["- userRepository: IamUserRepository",
         "- tokenRepository: IamTokenRepository"],
        ["+ revokeUserTokens(Long): void"],
        "left", "«service»", "caller", "node", "authService", order=0,
    )
    d.node(
        "role", "RoleService",
        ["- userRoleRepository: IamUserRoleRepository"],
        ["+ revokeAllRoles(Long, Long): void"],
        "left", "«service»", "caller", "node", "roleService", order=1,
    )
    d.node(
        "crypto", "CryptoComponent", [],
        ["+ hashPassword(String): String", "+ encrypt(String): String",
         "+ hash(String): String", "+ decrypt(String): String"],
        "left", "«component»", "infra", "node", "cryptoComponent", order=2,
    )
    d.node(
        "log", "LogService", [],
        ["+ recordPermissionChange(Long, ...): void"],
        "right", "«service»", "infra", "node", "logService",
    )
    d.node(
        "user_repo", "IamUserRepository", [],
        ["+ findById(Long): Optional<IamUser>",
         "+ findByUserNo(String): Optional<IamUser>",
         "+ save(IamUser): IamUser"],
        "bottom", "«interface · Spring Data JPA»", "interface", "node",
        "userRepository", order=0,
    )
    d.node(
        "org_repo", "IamOrgRepository", [],
        ["+ findById(Long): Optional<IamOrg>"],
        "bottom", "«interface · Spring Data JPA»", "interface", "node",
        "orgRepository", order=1,
    )
    return d.build()


def oop_account_class():
    d = ClassScene(
        "面向对象后端 · 账号管理用例类图",
        "以 AccountManagementUseCase 为中心：账号状态、调岗等规则由 UserAccount 自身承担。",
        "令牌回收与授权回收通过领域仓储端口完成；审计写入经 AuditTrail 端口后置执行。",
    )
    d.service(
        "account_uc", "AccountManagementUseCase",
        ["- userRepository: UserAccountRepository",
         "- organizationRepository: OrganizationRepository",
         "- roleGrantRepository: RoleGrantRepository",
         "- tokenRepository: TokenSessionRepository",
         "- crypto: AccountCrypto",
         "- auditTrail: AuditTrail",
         "- clock: Clock"],
        ["+ create(String, String, String, String, Long, String, Long): UserAccount",
         "+ page(int, int, Long, String): DomainPage<UserAccount>",
         "+ get(Long): UserAccount",
         "+ changeStatus(Long, String, String, Long): UserAccount",
         "+ transfer(Long, Long, String, Long): UserAccount",
         "+ toView(UserAccount): UserVO",
         "+ sensitive(Long): SensitiveUserVO"],
    )
    d.node(
        "controller", "UserController",
        ["- useCase: AccountManagementUseCase"],
        ["+ create(...): ApiResult<UserVO>",
         "+ page(...): ApiResult<UserPageVO>",
         "+ changeStatus(...): ApiResult<UserVO>",
         "+ transfer(...): ApiResult<UserVO>"],
        "top", "«controller»", "caller", "service", "useCase",
    )
    d.node(
        "crypto", "AccountCrypto",
        [],
        ["+ hashPassword(String): String", "+ encrypt(String): String",
         "+ decrypt(String): String", "+ maskName(String): String"],
        "left", "«interface · port»", "interface", "node", "crypto", order=0,
    )
    d.node(
        "audit", "AuditTrail", [],
        ["+ recordPermissionChange(Long, String, String, String, Object, Object): void"],
        "left", "«interface · port»", "interface", "node", "auditTrail", order=1,
    )
    d.node(
        "clock", "Clock", [],
        ["+ instant(): Instant", "+ getZone(): ZoneId"],
        "right", "«JDK»", "enum", "node", "clock",
    )
    d.node(
        "user_repo", "UserAccountRepository", [],
        ["+ findById(Long): Optional<UserAccount>",
         "+ findByUserNo(String): Optional<UserAccount>",
         "+ findPage(int, int, Long, String): DomainPage<UserAccount>",
         "+ save(UserAccount): UserAccount"],
        "bottom", "«interface · domain»", "interface", "node",
        "userRepository", order=0,
    )
    d.node(
        "org_repo", "OrganizationRepository", [],
        ["+ findById(Long): Optional<Organization>",
         "+ findByCode(String): Optional<Organization>"],
        "bottom", "«interface · domain»", "interface", "node",
        "organizationRepository", order=1,
    )
    d.node(
        "grant_repo", "RoleGrantRepository", [],
        ["+ findByUserId(Long): List<RoleGrant>",
         "+ save(RoleGrant): RoleGrant"],
        "bottom", "«interface · domain»", "interface", "node",
        "roleGrantRepository", order=2,
    )
    d.node(
        "token_repo", "TokenSessionRepository", [],
        ["+ findByTokenHash(String): Optional<TokenSession>",
         "+ revokeAllForUser(Long, Instant): void"],
        "bottom", "«interface · domain»", "interface", "node",
        "tokenRepository", order=3,
    )
    return d.build()


# ---------------------------------------------------------------------------
# 3. 组织管理
# ---------------------------------------------------------------------------

def structured_org_class():
    d = ClassScene(
        "结构化后端 · 组织管理类图",
        "以 OrganizationService 为中心：组织树通过 ancestors 前缀查询维护，移动时重写整棵子树。",
        "组织实体带 @Version 乐观锁；停用组织会级联停用全部下级。",
        min_width=1700,
    )
    d.service(
        "org", "OrganizationService",
        ["- orgRepository: IamOrgRepository",
         "- logService: LogService"],
        ["+ createOrg(String, String, String, Integer, Long, Integer): IamOrg",
         "+ updateOrg(Long, String, Integer, Long, String, Long): IamOrg",
         "+ getOrgTree(Long): List<IamOrg>",
         "+ getOrg(Long): IamOrg",
         "+ moveOrg(Long, Long): IamOrg",
         "+ getChildren(Long): List<IamOrg>",
         "- descendantsOf(IamOrg): List<IamOrg>"],
    )
    d.node(
        "controller", "OrganizationController",
        ["- organizationService: OrganizationService"],
        ["+ create(...): Result<OrganizationVO>",
         "+ update(...): Result<OrganizationVO>",
         "+ move(...): Result<OrganizationVO>",
         "+ tree(Long): Result<List<OrganizationVO>>"],
        "top", "«controller»", "caller", "service", "organizationService",
    )
    d.node(
        "log", "LogService", [],
        ["+ recordPermissionChange(Long, ...): void"],
        "right", "«service»", "infra", "node", "logService",
    )
    d.node(
        "org_repo", "IamOrgRepository", [],
        ["+ findById(Long): Optional<IamOrg>",
         "+ findByOrgCode(String): Optional<IamOrg>",
         "+ findByParentId(Long): List<IamOrg>",
         "+ findByAncestorsStartingWith(String): List<IamOrg>",
         "+ saveAll(List<IamOrg>): List<IamOrg>"],
        "bottom", "«interface · Spring Data JPA»", "interface", "node",
        "orgRepository", order=0,
    )
    return d.build()


def oop_org_class():
    d = ClassScene(
        "面向对象后端 · 组织管理用例类图",
        "以 OrganizationUseCase 为中心：树形规则由 Organization 值对象承担，用例只编排读取与保存。",
        "reparent / disable / withAncestors 均返回新的组织快照，避免调用方直接改字段。",
        min_width=1700,
    )
    d.service(
        "org_uc", "OrganizationUseCase",
        ["- repository: OrganizationRepository",
         "- auditTrail: AuditTrail"],
        ["+ create(String, String, String, Integer, Long, Integer, Long): Organization",
         "+ update(Long, String, Integer, Long, String, Long): Organization",
         "+ tree(Long): List<Organization>",
         "+ get(Long): Organization",
         "+ children(Long): List<Organization>",
         "+ move(Long, Long, Long): Organization",
         "- descendants(Organization): List<Organization>"],
    )
    d.node(
        "controller", "OrganizationController",
        ["- useCase: OrganizationUseCase"],
        ["+ create(...): ApiResult<OrganizationVO>",
         "+ update(...): ApiResult<OrganizationVO>",
         "+ move(...): ApiResult<OrganizationVO>",
         "+ tree(Long): ApiResult<List<OrganizationVO>>"],
        "top", "«controller»", "caller", "service", "useCase",
    )
    d.node(
        "audit", "AuditTrail", [],
        ["+ recordPermissionChange(Long, String, String, String, Object, Object): void"],
        "right", "«interface · port»", "interface", "node", "auditTrail",
    )
    d.node(
        "org_repo", "OrganizationRepository", [],
        ["+ findById(Long): Optional<Organization>",
         "+ findByCode(String): Optional<Organization>",
         "+ findRoots(): List<Organization>",
         "+ findByParentId(Long): List<Organization>",
         "+ findByAncestorsPrefix(String): List<Organization>",
         "+ save(Organization): Organization",
         "+ saveAll(List<Organization>): List<Organization>"],
        "bottom", "«interface · domain»", "interface", "node",
        "repository", order=0,
    )
    return d.build()


# ---------------------------------------------------------------------------
# 4. 权限与目录管理
# ---------------------------------------------------------------------------

def structured_permission_class():
    d = ClassScene(
        "结构化后端 · 权限目录类图",
        "以 PermissionService 为中心：新建权限先校验系统存在，绑定 API 时校验两侧同属一个系统。",
        "系统登记由 SystemService.registerSystem 完成，API 登记由 ApiService.registerApi 完成。",
        min_width=1800,
    )
    d.service(
        "permission", "PermissionService",
        ["- permissionRepository: IamPermissionRepository",
         "- apiRepository: IamApiRepository",
         "- systemService: SystemService"],
        ["+ createPermission(Long, Long, String, String, String, String, String, Integer): IamPermission",
         "+ getPermission(Long): IamPermission",
         "+ listPermissions(Long): List<IamPermission>",
         "+ bindApi(Long, Long): IamApi",
         "+ unbindApi(Long, Long): void",
         "+ getPermissionApis(Long): List<IamApi>"],
    )
    d.node(
        "controller", "PermissionController",
        ["- permissionService: PermissionService"],
        ["+ create(...): Result<PermissionVO>",
         "+ list(Long): Result<List<PermissionVO>>",
         "+ bindApi(Long, Long): Result<ApiVO>",
         "+ unbindApi(Long, Long): Result<Void>"],
        "top", "«controller»", "caller", "service", "permissionService",
    )
    d.node(
        "system", "SystemService",
        ["- systemRepository: IamSystemRepository"],
        ["+ getSystem(Long): IamSystem",
         "+ registerSystem(String, String, String): IamSystem"],
        "left", "«service»", "caller", "node", "systemService",
    )
    d.node(
        "perm_repo", "IamPermissionRepository", [],
        ["+ findById(Long): Optional<IamPermission>",
         "+ findBySystemIdAndPermissionCode(...): Optional<IamPermission>",
         "+ findBySystemIdAndIsDeleted(Long, Integer): List<IamPermission>",
         "+ save(IamPermission): IamPermission"],
        "bottom", "«interface · Spring Data JPA»", "interface", "node",
        "permissionRepository", order=0,
    )
    d.node(
        "api_repo", "IamApiRepository", [],
        ["+ findById(Long): Optional<IamApi>",
         "+ findByApiPathAndHttpMethod(String, String): Optional<IamApi>",
         "+ findBySystemIdAndApiCode(Long, String): Optional<IamApi>",
         "+ findByPermissionId(Long): List<IamApi>",
         "+ save(IamApi): IamApi"],
        "bottom", "«interface · Spring Data JPA»", "interface", "node",
        "apiRepository", order=1,
    )
    d.node(
        "system_repo", "IamSystemRepository", [],
        ["+ findById(Long): Optional<IamSystem>",
         "+ findBySystemCode(String): Optional<IamSystem>"],
        "bottom", "«interface · Spring Data JPA»", "interface", "node",
        order=2, kind="dependency",
    )
    return d.build()


def oop_permission_class():
    d = ClassScene(
        "面向对象后端 · 权限目录用例类图",
        "以 PermissionCatalogUseCase 为中心：管理侧端口与查询侧端口分离，API 绑定返回新的 ApiEndpoint 快照。",
        "系统登记由 SystemManagementUseCase、API 登记由 ApiCatalogUseCase 负责。",
        min_width=1900,
    )
    d.service(
        "permission_uc", "PermissionCatalogUseCase",
        ["- permissionRepository: PermissionAdministrationRepository",
         "- apiRepository: ApiAdministrationRepository",
         "- systemRepository: SystemRepository"],
        ["+ create(Long, Long, String, String, String, String, OperationAction, Integer): PermissionNode",
         "+ list(Long): List<PermissionNode>",
         "+ bindApi(Long, Long): ApiEndpoint",
         "+ unbindApi(Long, Long): void",
         "+ permissionApis(Long): List<ApiEndpoint>"],
    )
    d.node(
        "controller", "PermissionController",
        ["- useCase: PermissionCatalogUseCase"],
        ["+ create(...): ApiResult<PermissionVO>",
         "+ list(Long): ApiResult<List<PermissionVO>>",
         "+ bindApi(Long, Long): ApiResult<ApiVO>",
         "+ unbindApi(Long, Long): ApiResult<Void>"],
        "top", "«controller»", "caller", "service", "useCase",
    )
    d.node(
        "perm_repo", "PermissionAdministrationRepository", [],
        ["+ findById(Long): Optional<PermissionNode>",
         "+ findBySystemAndCode(Long, String): Optional<PermissionNode>",
         "+ findBySystem(Long): List<PermissionNode>",
         "+ save(PermissionNode): PermissionNode"],
        "bottom", "«interface · domain»", "interface", "node",
        "permissionRepository", order=0,
    )
    d.node(
        "api_repo", "ApiAdministrationRepository", [],
        ["+ findById(Long): Optional<ApiEndpoint>",
         "+ findBySystemAndCode(Long, String): Optional<ApiEndpoint>",
         "+ findByPathAndMethod(String, String): Optional<ApiEndpoint>",
         "+ findByPermission(Long): List<ApiEndpoint>",
         "+ save(ApiEndpoint): ApiEndpoint"],
        "bottom", "«interface · domain»", "interface", "node",
        "apiRepository", order=1,
    )
    d.node(
        "system_repo", "SystemRepository", [],
        ["+ findById(Long): Optional<BusinessSystem>",
         "+ findByCode(String): Optional<BusinessSystem>",
         "+ findAll(): List<BusinessSystem>",
         "+ save(BusinessSystem): BusinessSystem"],
        "bottom", "«interface · domain»", "interface", "node",
        "systemRepository", order=2,
    )
    return d.build()


# ---------------------------------------------------------------------------
# 5. 审计日志
# ---------------------------------------------------------------------------

def structured_audit_class():
    d = ClassScene(
        "结构化后端 · 审计日志类图",
        "以 LogService 为中心：拦截器与日志控制器都写入同一服务；查询使用 JPA Specification 动态过滤。",
        "record / recordPermissionChange 使用 REQUIRES_NEW，业务回滚不影响审计落库；导出上限 100000 行。",
        min_width=1900,
    )
    d.service(
        "log", "LogService",
        ["- logRepository: IamLogRepository",
         "- objectMapper: ObjectMapper"],
        ["+ record(Long, String, String, String, String, String, String, boolean, String, String): void",
         "+ recordPermissionChange(Long, String, String, String, Object, Object, String, String): void",
         "+ queryLogs(Pageable, Long, String, String, String, String, Integer, LocalDateTime, LocalDateTime): Page<IamLog>",
         "+ exportLogs(Long, String, String, String, String, Integer, LocalDateTime, LocalDateTime): List<IamLog>",
         "+ toCsv(List<IamLog>): String",
         "+ getUserHistory(Long, Pageable): Page<IamLog>"],
    )
    d.node(
        "controller", "LogController",
        ["- logService: LogService"],
        ["+ list(...): Result<PageResult<LogVO>>",
         "+ export(...): ResponseEntity<byte[]>"],
        "top", "«controller»", "caller", "service", "logService", order=0,
    )
    d.node(
        "interceptor", "AuthInterceptor",
        ["- authService: AuthService", "- apiService: ApiService",
         "- decisionService: DecisionService", "- logService: LogService"],
        ["+ preHandle(...): boolean", "+ afterCompletion(...): void",
         "- recordAudit(HttpServletRequest, HttpServletResponse, Exception): void"],
        "top", "«component»", "caller", "service", "logService", order=1,
    )
    d.node(
        "mapper", "ObjectMapper", [],
        ["+ writeValueAsString(Object): String"],
        "left", "«JDK»", "enum", "node", "objectMapper",
    )
    d.node(
        "log_repo", "IamLogRepository", [],
        ["+ findAll(Specification<IamLog>, Pageable): Page<IamLog>",
         "+ save(IamLog): IamLog",
         "+ findByUserId(Long, Pageable): Page<IamLog>"],
        "bottom", "«interface · Spring Data JPA»", "interface", "node",
        "logRepository", order=0,
    )
    return d.build()


def oop_audit_class():
    d = ClassScene(
        "面向对象后端 · 审计日志用例类图",
        "以 AuditQueryUseCase 为中心：查询条件由 AuditQuery 值对象承载，仓储只负责分页与导出查询。",
        "写路径由 AuditTrail 端口完成（REQUIRES_NEW）；本页聚焦查询与 CSV 导出。",
        min_width=1700,
    )
    d.service(
        "audit_uc", "AuditQueryUseCase",
        ["- repository: AuditQueryRepository"],
        ["+ page(AuditQuery, int, int): DomainPage<AuditEntry>",
         "+ export(AuditQuery): List<AuditEntry>",
         "+ csv(List<AuditEntry>): String",
         "- validate(AuditQuery): AuditQuery"],
    )
    d.node(
        "controller", "LogController",
        ["- useCase: AuditQueryUseCase"],
        ["+ list(...): ApiResult<LogPageVO>",
         "+ export(...): ResponseEntity<byte[]>"],
        "top", "«controller»", "caller", "service", "useCase",
    )
    d.node(
        "repo", "AuditQueryRepository", [],
        ["+ findPage(AuditQuery, int, int): DomainPage<AuditEntry>",
         "+ findForExport(AuditQuery, int): List<AuditEntry>"],
        "bottom", "«interface · domain»", "interface", "node",
        "repository", order=0,
    )
    return d.build()

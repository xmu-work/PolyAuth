# RBAC 后端 API 使用手册

> 版本：2.1
> 更新日期：2026-11-22
> 适用范围：`backend` 模块的 `service.structured` 与 `service.ddd` 双实现

本手册按当前 Controller、DTO/VO、异常处理和数据库初始化脚本重写。旧的
`/roles/grants`、`/systems/modules`、`/systems/function-points` 和
`/systems/role-functions` 已不属于当前 API。

## 1. 服务地址与实现范围

| 实现 | 默认地址 | 功能范围 |
|---|---|---|
| `app.backend-strategy=structured` | `http://localhost:8080/api/v1` | RBAC3 管理能力与完整统一身份接口 |
| `app.backend-strategy=ddd` | `http://localhost:8080/api/v1` | 与结构化策略相同的 52 个功能权限端点 |

两种策略使用同一端口和上下文路径 `/api/v1`，完整请求地址需要再加表中的相对路径，
例如 `/api/v1/users`。两种策略使用相同的外部 API 契约：

| 能力 | structured | ddd |
|---|---|---|
| 登录、登出、令牌校验 | 已实现 | 已实现 |
| `operationId` API 权限判断 | 已实现 | 已实现 |
| 当前用户、权限码、部门、统一身份用户查询 | 已实现 | 已实现 |
| 用户账号、组织、系统、权限树、API 目录 | 已实现 | 已实现 |
| 角色创建、查询、修改 | 已实现 | 已实现 |
| 用户角色授予、撤销、按用户查询 | 已实现 | 已实现 |
| 角色权限绑定、查询、解绑 | 已实现 | 已实现 |
| 角色互斥、审计、日志导出 | 已实现 | 已实现 |
| OpenAPI 签名与资源联合鉴权 | 已实现 | 已实现 |

端点覆盖由 `ApiParityContractTest` 校验；真实 MySQL 联调需分别以两种策略启动后端后运行契约测试。

## 2. 统一身份外部契约

课程项目管理系统使用的 10 个接口以
[docs/07_统一身份外部契约.md](docs/07_统一身份外部契约.md) 为唯一契约：

| 方法 | 路径 | 说明 | ddd |
|---|---|---|---|
| POST | `/auth/login` | 账号密码登录 | 支持 |
| POST | `/auth/logout` | 吊销当前令牌，成功返回 204 | 支持 |
| GET | `/auth/me` | 当前用户、外部角色和前端权限码 | 支持 |
| GET | `/auth/permissions/{permission}` | 校验单个前端权限码 | 支持 |
| GET | `/auth/departments` | 部门分页 | 支持 |
| GET | `/auth/api-permissions/{operationId}` | 按 API operationId 判断调用权限 | 支持 |
| GET | `/auth/departments/{departmentId}/users` | 部门用户分页 | 支持 |
| GET | `/auth/users/{userId}` | 按统一身份用户标识查询用户 | 支持 |
| GET | `/auth/audit-logs` | 分页查询外部业务审计日志 | 支持 |
| POST | `/auth/audit-logs` | 按 `requestId` 幂等写入外部业务审计日志 | 支持 |

这 10 个接口成功时直接返回业务对象，不使用管理端 `Result` 包装。失败时返回：

```json
{
  "code": "UNAUTHORIZED",
  "message": "请先登录"
}
```

统一身份用户标识是 `iam_user.user_no`，JWT `sub` 和 `/auth/me.subject` 均使用该值。
登录响应中的 `userId` 是本地数据库主键字符串，仅用于管理后台兼容；调用
`/auth/users/{userId}` 时不能直接使用登录响应中的本地 `userId`。

## 3. 通用响应与认证

除统一身份 10 个接口外，后端使用统一响应包：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "requestId": "调用方或服务端生成的请求 ID",
  "timestamp": "2026-11-22T20:00:00"
}
```

- 成功业务码为 `0`。
- 无业务数据时省略 `data`。
- 分页数据为 `{ "total", "page", "pageSize", "list" }`。
- 请求参数 `X-Request-Id` 会回写到响应头和 `Result.requestId`。
- 写请求可携带 `Idempotency-Key`；相同调用者、相同请求重试会重放首次成功响应，
  不同请求复用同一键返回 `1004`。

管理端使用请求头：

```text
Authorization: Bearer <accessToken>
```

管理端接口必须匹配已登记的 `iam_api(api_path, http_method)`，并拥有映射的前端权限。
API 未登记、未映射权限或用户无权限均默认拒绝，返回 `403/2007`。

`/open/**` 支持两种认证：

1. 普通用户 Bearer 令牌，只能查询本人。
2. 接入方签名：`X-App-Id`、`X-Timestamp`、`X-Nonce`、`X-Signature`。

## 4. 请求体与查询参数

### 4.1 JSON 请求体

以下接口使用 JSON Body：

| 方法 | 路径 | 请求体 |
|---|---|---|
| POST | `/auth/login` | 登录对象 |
| PUT | `/roles/{roleId}` | `UpdateRoleDTO` |
| POST | `/roles/{roleId}/permissions` | 权限主键裸数组 `[10001,10002]` |
| DELETE | `/roles/{roleId}/permissions` | 权限主键裸数组 |

角色更新示例：

```json
{
  "roleName": "课程管理员",
  "description": "更新说明",
  "status": "ACTIVE",
  "parentId": 3
}
```

### 4.2 Query/Form 参数

除明确使用 JSON Body 的接口外，Controller 使用 `@RequestParam`，或由 Spring 从
query/form 参数绑定 DTO。前端统一封装为 `postForm`、`putForm`、`deleteForm`。

典型 DTO 表单入口：

- `PUT /users/{userId}/organization`
- `PUT /organizations/{orgId}`

## 5. 结构化后端接口

### 5.1 认证

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/login` | 统一身份登录，直接返回登录对象 |
| POST | `/auth/logout` | 吊销当前令牌，返回 204 |
| GET | `/auth/validate` | 校验当前令牌，返回 `Result<Boolean>` |
| PUT | `/auth/password` | 自助修改密码，参数 `oldPassword`、`newPassword` |
| GET | `/auth/me` | 当前用户、角色和权限码 |
| GET | `/auth/permissions/{permission}` | 校验单个前端权限码 |
| GET | `/auth/departments` | 按 `parentId` 分页查询直接下级部门 |
| GET | `/auth/api-permissions/{operationId}` | 按 `api_code` 判断 API 权限 |
| GET | `/auth/departments/{departmentId}/users` | 部门用户分页 |
| GET | `/auth/users/{userId}` | 按 `user_no` 查询统一身份用户 |

`GET /auth/departments` 和部门用户接口支持：

- `keyword`：可选，最大 100 字符。
- `page`：默认 1。
- `pageSize`：默认 20，最大 100。
- 部门接口还支持 `parentId`。

不存在的 `operationId` 返回 `404/API_OPERATION_NOT_FOUND`。查询统一身份用户需要目标
系统的 `TEC` 权限，无权限返回 `403/FORBIDDEN`。

### 5.2 用户

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/users` | 创建用户；query/form 参数 |
| GET | `/users` | 用户分页；支持 `page`、`size`、`orgId`、`accountStatus` |
| GET | `/users/{userId}` | 用户详情，返回脱敏 `UserVO` |
| GET | `/users/{userId}/sensitive` | 查询姓名和电话明文，需要独立权限 |
| PUT | `/users/{userId}/status` | 变更 `targetStatus`，可选 `reason` |
| PUT | `/users/{userId}/organization` | 调岗；表单字段 `orgId`、`post` |

创建用户字段为 `userNo`、`realName`、`orgId`，可选 `password`、`phone`、`post`。
分页响应字段为 `total/page/pageSize/list`。

### 5.3 角色、继承与互斥

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/roles` | 创建角色 |
| GET | `/roles` | 查询全部未删除角色 |
| GET | `/roles/{roleId}` | 角色详情 |
| PUT | `/roles/{roleId}` | JSON 更新名称、描述、状态、父角色 |
| GET | `/roles/{roleId}/exclusions` | 查询互斥角色 ID |
| POST | `/roles/{roleId}/exclusions` | 新建互斥；参数 `excludedRoleId` |
| DELETE | `/roles/{roleId}/exclusions/{excludedRoleId}` | 删除互斥 |

角色继承通过 `parentId` 表示。创建或修改导致自引用或继承成环时返回
`409/3002 ROLE_INHERIT_CYCLE`。

### 5.4 用户角色分配

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/roles/{roleId}/assignments` | 向用户授予角色 |
| DELETE | `/roles/{roleId}/assignments/{userId}` | 撤销用户角色 |
| GET | `/roles/{roleId}/assignments` | 查询角色下的分配 |
| GET | `/roles/assignments?userId=` | 查询用户的全部角色关系 |

授予参数为 `userId`、可选 `expireTime`、可选 `grantReason`。`grantBy` 和权限变更
审计操作人由服务端从认证上下文取得，不能由客户端伪造。

### 5.5 角色权限、前端权限树与 API 目录

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/roles/{roleId}/permissions?allowedAction=` | JSON 数组授予角色前端权限 |
| DELETE | `/roles/{roleId}/permissions` | JSON 数组解除角色前端权限 |
| GET | `/roles/{roleId}/permissions` | 查询角色已绑定权限 |
| POST | `/permissions` | 登记菜单或按钮 |
| GET | `/permissions?systemId=` | 查询系统权限树节点 |
| POST | `/permissions/{permissionId}/apis/{apiId}` | API 映射到前端权限 |
| DELETE | `/permissions/{permissionId}/apis/{apiId}` | 解除 API 映射 |
| GET | `/permissions/{permissionId}/apis` | 查询权限映射的 API |
| POST | `/apis` | 登记后端 API |
| GET | `/apis?systemId=` | 查询系统 API |
| GET | `/apis/{apiId}` | API 详情 |

`allowedAction` 及权限 `action` 支持：

```text
NONE, VIEW, ALL, VIEW_EDIT, VIEW_ADD, VIEW_APPROVE, VIEW_EXPORT,
VIEW_EDIT_APPROVE, VIEW_ADD_EDIT, VIEW_EDIT_EXPORT, VIEW_SELF, VIEW_EDIT_SELF
```

`NONE` 不产生授权；绑定动作必须覆盖权限声明的动作。

### 5.6 系统

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/systems` | 登记接入系统 |
| GET | `/systems` | 查询接入系统 |
| GET | `/systems/{systemId}` | 系统详情 |

### 5.7 组织机构

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/organizations` | 创建组织 |
| GET | `/organizations` | 顶层组织；可选 `rootId` 查询子树扁平列表 |
| GET | `/organizations/{orgId}` | 组织详情 |
| GET | `/organizations/{orgId}/children` | 直接下级 |
| PUT | `/organizations/{orgId}/parent` | 移动节点；参数 `newParentId` |
| PUT | `/organizations/{orgId}` | 修改名称、排序、负责人和启停状态 |

组织类型为 `HQ/BRANCH/DEPT/PROJECT`，对应层级 1～4。

### 5.8 OpenAPI

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/open/permissions` | 查询用户前端权限主键集合 |

普通用户令牌只能访问本人；应用签名模式可按功能权限查询用户权限集合。

### 5.9 日志

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/logs` | 统一日志分页查询 |
| GET | `/logs/export` | 按相同筛选条件导出 CSV |

查询参数包括 `page`、`size`、`userId`、`systemCode`、`logType`、`username`、
`action`、`success`、`startTime`、`endTime`。日志包含普通操作和权限变更，
权限变更记录 `beforeSnapshot`、`afterSnapshot`。

## 6. ddd 策略接口覆盖

`ddd` 策略实现第 2、5 节列出的全部 52 个功能权限端点，路径、HTTP 方法、请求参数、
响应结构和业务码与结构化后端一致。内部实现不同：

- Controller 将 HTTP DTO 转为应用 Command，并只返回 VO 或外部契约 record。
- `application/usecase` 负责事务和用例编排。
- 领域对象维护账号、组织、角色、授权和权限节点不变量。
- `domain/repository` 只使用领域对象和 JDK 容器。
- JPA Entity、Mapper 和 Adapter 位于 `infrastructure/persistence`。

端点集合由 `ApiParityContractTest` 静态校验；共享 HTTP 场景由
[backend/README.md](../backend/README.md) 说明。

## 7. 错误码

管理端稳定错误码：

| 业务码 | 名称 | HTTP |
|---|---|---|
| 1001 | PARAM_INVALID | 400 |
| 1004 | IDEMPOTENCY_CONFLICT | 409 |
| 1005 | RESOURCE_NOT_FOUND | 404 |
| 1006 | METHOD_NOT_ALLOWED | 405 |
| 2001 | UNAUTHENTICATED | 401 |
| 2002 | TOKEN_INVALID | 401 |
| 2003 | TOKEN_EXPIRED | 401 |
| 2004 | TOKEN_REVOKED | 401 |
| 2005 | ACCOUNT_SEALED | 403 |
| 2006 | ACCOUNT_DISABLED | 403 |
| 2007 | NO_FUNCTION_PERM | 403 |
| 2009 | SIGNATURE_INVALID | 401 |
| 2010 | REPLAY_DETECTED | 401 |
| 2011 | APP_DISABLED | 403 |
| 3002 | ROLE_INHERIT_CYCLE | 409 |
| 3003 | ORG_MOVE_INVALID | 409 |
| 3004 | USER_ALREADY_SEALED | 409 |
| 3005 | GRANT_DUPLICATE | 409 |
| 3008 | LOCK_ACQUIRE_FAILED | 409 |
| 3009 | RESOURCE_DUPLICATE | 409 |
| 3010 | TARGET_USER_ABNORMAL | 409 |
| 3011 | ROLE_EXCLUSION_CONFLICT | 409 |
| 5000 | INTERNAL_ERROR | 500 |
| 5001 | SERVICE_UNAVAILABLE | 503 |

统一身份字符串错误码：

| 错误码 | HTTP | 触发场景 |
|---|---|---|
| `BAD_REQUEST` | 400 | 参数、请求体或路径变量非法 |
| `UNAUTHORIZED` | 401 | 未认证、令牌无效/过期/吊销 |
| `FORBIDDEN` | 403 | 账号状态不允许或缺少 `TEC` 等业务权限 |
| `API_OPERATION_NOT_FOUND` | 404 | `operationId` 不存在 |
| `IDENTITY_DEPARTMENT_NOT_FOUND` | 404 | 统一身份部门不存在 |
| `IDENTITY_USER_NOT_FOUND` | 404 | 统一身份用户不存在 |
| `CONFLICT` | 409 | 统一身份数据冲突 |
| `IDENTITY_SERVICE_UNAVAILABLE` | 503 | 统一身份依赖暂不可用 |
| `INTERNAL_ERROR` | 500 | 未分类内部错误 |

## 8. 调用示例

```powershell
$base = 'http://localhost:8080/api/v1'
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" `
  -ContentType 'application/json' `
  -Body '{"username":"admin","password":"Admin@123","systems":["ADMIN"]}'
$token = $login.accessToken

Invoke-RestMethod -Uri "$base/roles" `
  -Headers @{ Authorization = "Bearer $token" }

Invoke-RestMethod -Method Put -Uri "$base/roles/12" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType 'application/json' `
  -Body '{"roleName":"课程管理员","status":"ACTIVE","parentId":3}'
```

## 9. 源码追溯

- 共享接口层：`backend/src/main/java/com/polyauth/api`
- structured 实现：`backend/src/main/java/com/polyauth/service/structured`
- ddd 实现：`backend/src/main/java/com/polyauth/service/ddd`
- 双后端调用规则：`backend/src/test/java/com/polyauth/contract/DualBackendContractTest.java`
- 统一身份契约：`docs/07_统一身份外部契约.md`
- 初始化与升级脚本：`backend/src/main/resources/backend/src/main/resources/db/schema.sql`、`seed.sql`、`rbac3_upgrade.sql`、
  `identity_course_upgrade.sql`

普通 `mvn test` 会在未指定 `contract.baseUrl` 时跳过黑盒契约测试。
真实联调时应分别以 `structured` 与 `ddd` 策略启动后端，再执行：

```powershell
cd backend
.\run-dual-contracts.ps1
```

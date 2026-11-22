# 统一应用软件权限管理系统（IAM）

基于 RBAC3 模型的企业统一身份与**功能权限 + 数据权限**管理平台。系统集中管理多系统、前端菜单/按钮权限、后端 API、账号、组织、角色、角色继承、角色互斥、数据范围与审计，并通过 OpenAPI 为接入系统提供联合鉴权。

## 项目边界

本项目实现课程要求的 RBAC3 核心能力。

- “能访问哪个菜单、页面和按钮”由角色与前端权限树决定；“能调用哪个后端 API”再由 API 到权限的映射独立校验。
- “能查看哪个分公司、部门、项目或本人的数据”由角色数据范围与目标组织/项目/用户联合判定。
- 组织机构同时作为账号归属主数据和分公司、部门、项目范围的映射依据。
- 角色支持功能角色与岗位型数据角色，均支持继承；互斥角色不能同时授予同一用户。

明确不包含的能力包括：ABAC、审批流、跨系统 SSO 跳转、刷新令牌、权限变更推送、明文敏感信息导出。

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.4.5 |
| 持久层 | Spring Data JPA、MySQL 8.0 |
| 缓存与幂等 | Redis、Spring Data Redis |
| 安全 | JWT、PBKDF2、AES-256-GCM、HMAC-SHA256 |
| 数据源 | Druid |
| 校验与工具 | Spring Validation、Lombok |
| 构建 | Maven |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Element Plus |

## 已实现能力

### 身份认证与账号

- 账号密码登录、令牌签发、登出和令牌校验。
- 自助修改密码。
- 用户创建、详情、分页查询、状态变更与离职封存。
- 普通用户视图中的姓名、电话默认脱敏；姓名和电话明文仅由独立功能点保护的接口返回，并自动审计。
- 封存时回收用户全部角色并吊销令牌。

### 组织与角色

- 组织机构树、详情、子节点、新建、修改、停用和节点移动。
- 功能角色、岗位型数据角色创建、详情和列表。
- 角色父子继承、循环继承校验、角色互斥配置与授予时冲突校验。
- 员工调岗，更新组织与岗位并保留授权供人工复核。
- 角色继承链在权限判定时生效。
- 角色生命周期、用户角色分配和角色权限分配统一由 `RoleService` 承载。

### 功能权限

- 多系统登记；每个系统拥有独立的前端权限树与后端 API 目录。
- 菜单和按钮共用 `iam_permission` 自关联树，`MENU/BUTTON` 区分节点类型。
- 后端 `iam_api` 独立建模，通过 `permission_id` 映射到前端权限；一个权限可映射多个 API。
- 角色与前端权限批量绑定、解绑和查询；系统、权限、角色和账号状态参与判定。
- 单 API、批量 API 和前端权限集合查询。
- 全公司、本分公司、本部门、本项目、本人五类数据范围。
- 功能权限与数据权限联合判定；角色继承链上的数据范围自动合并。
- 数据范围临时授权、到期自动失效、回收与前后快照。

### 授权与安全

- 用户角色授予、撤销和查询，支持授权有效期。
- 互斥角色授予冲突拒绝，授权、回收、角色互斥和数据范围写操作使用 Redis 分布式锁。
- 管理员不能授予超出自身功能权限的角色。
- 按请求定位 API 后，再检查 API 所属权限是否授予；未登记或未映射权限的 API 默认拒绝（fail-closed）。
- OpenAPI 支持接入方 HMAC 签名、时间戳、nonce 防重放；普通用户令牌只能查询本人。
- 关键请求参数校验、分页上限、批量上限、唯一约束和乐观锁。
- 写请求支持 `Idempotency-Key`，请求指纹包含调用者、参数和请求体。
- 支持 `X-Request-Id` 回写、统一响应 `requestId` 和日志关联。

### 统一日志与缓存

- 普通操作日志和权限变更日志统一保存在 `iam_log`，通过 `log_type` 区分。
- 权限授予、回收、角色权限绑定/解绑记录变更前后 JSON 快照。
- 统一日志分页查询，支持用户、用户名、系统、类型、动作、成功状态和时间范围过滤。
- 审计日志 CSV 导出，包含公式注入防护；登录成败和只读鉴权请求均留痕。
- 数据库触发器拒绝修改和删除 `iam_log`，保证只追加。
- 权限缓存使用版本号失效，不使用 Redis `KEYS` 扫描。
- 分布式锁使用 owner token 与原子解锁。

## 架构

```text
┌─────────────────────────────────────────────────┐
│ web：Vue 管理后台                               │
├─────────────────────────────────────────────────┤
│ interfaces：REST Controller / Filter / Advice   │
├─────────────────────────────────────────────────┤
│ application：认证、账号、组织、角色、授权、PDP │
├─────────────────────────────────────────────────┤
│ infrastructure：JPA、Redis、Crypto、Cache       │
└─────────────────────────────────────────────────┘
```

核心目录：

```text
RBAC/
├── src/main/java/com/iam/platform/
│   ├── interfaces/        REST 接口、认证鉴权、请求追踪与幂等
│   ├── application/       业务服务与功能权限决策
│   ├── common/            统一响应、异常、枚举
│   └── infrastructure/    JPA、Redis、缓存、加解密
├── src/main/resources/db/ schema.sql、seed.sql
├── src/test/java/         后端测试
├── web/                   Vue 管理后台
└── design/                详细设计与接口设计材料
```

数据库核心模型为：

```text
多系统 1:N 前端权限树 1:N 后端 API
用户 M:N 角色 M:N 前端权限
分层组织 1:N 用户
```

普通操作与权限变更统一保存在 `iam_log`；统一日志、令牌等技术支撑表不属于 RBAC 核心 E-R 图。角色互斥由 `iam_role_exclusion` 建模，角色数据范围由 `iam_role_data_scope` 建模。

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6+
- Node.js 20+、pnpm

### 初始化数据库

```bash
mysql -u root -p -e "CREATE DATABASE iam_platform DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -u root -p iam_platform < src/main/resources/db/schema.sql
mysql -u root -p iam_platform < src/main/resources/db/seed.sql
```

已部署 RBAC0/RBAC1 数据库升级时，先执行 `src/main/resources/db/rbac3_upgrade.sql`，再重启应用。

初始化管理员为 `admin / Admin@123`。该密码仅用于本地初始化，部署前必须修改。

MySQL 建议设置 `default-time-zone = '+08:00'`；`seed.sql` 已在会话级设置北京时间。

### 关键环境变量

| 变量 | 默认值或用途 |
| --- | --- |
| `DB_HOST` / `DB_PORT` | MySQL 地址与端口 |
| `DB_NAME` | 默认 `iam_platform` |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号与密码 |
| `DB_SSL` | MySQL SSL 开关，默认 `false` |
| `REDIS_HOST` / `REDIS_PORT` | Redis 地址与端口 |
| `REDIS_PASSWORD` | Redis 密码 |
| `JWT_SECRET` | JWT 密钥 |
| `AES_KEY` | 敏感字段加密密钥 |
| `HMAC_KEY` | 检索哈希与签名密钥 |

生产环境必须通过密钥或环境变量注入，不得沿用仓库中的开发默认值。

### 后端

```bash
mvn clean test
mvn clean package -DskipTests
java -jar target/iam-platform-1.0.0-SNAPSHOT.jar
```

后端默认地址：`http://localhost:8080/api/v1`。

### 前端

```bash
cd web
pnpm install
pnpm dev
pnpm typecheck
pnpm build
```

前端通过 Vite 代理访问真实后端；不包含 Mock 插件、Mock 数据或 Mock 开关。

## 鉴权规则

- `POST /auth/login` 为公开接口。
- 管理端请求携带 `Authorization: Bearer <token>`。
- 管理端请求必须先匹配已登记的 `iam_api`，再检查该 API 映射的前端权限；任一环节缺失都默认拒绝。
- OpenAPI 可携带 `X-App-Id`、`X-Timestamp`、`X-Nonce`、`X-Signature` 进行接入方认证。
- OpenAPI 普通用户令牌只能查询本人；接入方签名可查询授权范围内的用户。
- `/open/authorize/resource` 同时接收功能 API 与目标组织、项目或用户上下文，功能权限和数据范围都通过才放行。

## API 概览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/auth/login` | 登录 |
| POST | `/api/v1/auth/logout` | 登出 |
| GET | `/api/v1/auth/validate` | 校验令牌 |
| PUT | `/api/v1/auth/password` | 自助修改密码 |
| GET/POST | `/api/v1/users` | 用户分页查询 / 创建 |
| GET | `/api/v1/users/{userId}` | 用户详情 |
| GET | `/api/v1/users/{userId}/sensitive` | 受独立权限保护地查看姓名/电话明文 |
| PUT | `/api/v1/users/{userId}/status` | 变更账号状态 |
| PUT | `/api/v1/users/{userId}/organization` | 员工调岗 |
| GET/POST | `/api/v1/organizations` | 组织树 / 新建组织 |
| GET | `/api/v1/organizations/{orgId}` | 组织详情 |
| GET | `/api/v1/organizations/{orgId}/children` | 下级组织 |
| PUT | `/api/v1/organizations/{orgId}/parent` | 调整父组织 |
| PUT | `/api/v1/organizations/{orgId}` | 修改或停用组织 |
| GET/POST | `/api/v1/roles` | 角色列表 / 新建功能或数据角色 |
| GET | `/api/v1/roles/{roleId}` | 角色详情 |
| PUT | `/api/v1/roles/{roleId}` | 修改角色、状态和父角色 |
| GET/POST | `/api/v1/roles/{roleId}/exclusions` | 查询 / 新建角色互斥 |
| DELETE | `/api/v1/roles/{roleId}/exclusions/{excludedRoleId}` | 解除角色互斥 |
| GET/POST | `/api/v1/roles/{roleId}/data-scopes` | 查询 / 授予角色数据范围 |
| DELETE | `/api/v1/roles/{roleId}/data-scopes/{bindingId}` | 回收角色数据范围 |
| POST | `/api/v1/roles/{roleId}/assignments` | 将角色分配给用户 |
| DELETE | `/api/v1/roles/{roleId}/assignments/{userId}` | 撤销用户角色 |
| GET | `/api/v1/roles/{roleId}/assignments` | 查询角色下的用户分配 |
| GET | `/api/v1/roles/assignments` | 按用户查询角色分配 |
| POST/DELETE/GET | `/api/v1/roles/{roleId}/permissions` | 角色权限授予 / 解除 / 查询 |
| GET/POST | `/api/v1/systems` | 子系统列表 / 登记 |
| GET/POST | `/api/v1/permissions` | 前端权限树查询 / 登记 |
| POST/DELETE | `/api/v1/permissions/{permissionId}/apis/{apiId}` | 绑定 / 解除 API 映射 |
| GET | `/api/v1/permissions/{permissionId}/apis` | 查询权限下的 API |
| GET/POST | `/api/v1/apis` | 后端 API 列表 / 登记 |
| GET | `/api/v1/apis/{apiId}` | 后端 API 详情 |
| GET | `/api/v1/open/authorize` | 按 `apiId` 做单点后端鉴权 |
| POST | `/api/v1/open/authorize/batch` | 按 `apiIds` 批量后端鉴权 |
| GET | `/api/v1/open/permissions` | 查询用户前端菜单/按钮权限集合 |
| GET | `/api/v1/open/data-scopes` | 查询用户当前生效的数据范围 |
| POST | `/api/v1/open/authorize/resource` | 功能权限与数据权限联合判定 |
| GET/POST | `/api/v1/systems/data-scopes` | 数据范围字典查询 / 新建 |
| GET | `/api/v1/logs` | 统一日志分页查询（普通操作 + 权限变更） |
| GET | `/api/v1/logs/export` | 导出统一日志 CSV |

## 验证状态

后端共 102 项测试全部通过，覆盖认证、账号、组织、角色继承/互斥/分配、数据权限联合决策、临时授权到期回收、权限树、API 映射、OpenAPI、统一日志、输入校验、并发冲突、请求追踪和幂等；前端通过 `pnpm typecheck`、`pnpm build` 和 `pnpm lint`。

`design/` 目录保留课程详细设计材料；功能范围以本 README 和当前代码为准。

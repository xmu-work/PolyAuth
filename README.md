# PolyAuth：多系统统一权限管理平台

PolyAuth 是面向多业务系统的统一身份与功能权限平台，集中管理系统、前端菜单/按钮权限、后端 API、账号、组织、角色继承、角色互斥和审计。项目采用**同应用、同 API、同数据库、双 Service 实现**：启动时通过 `app.backend-strategy=structured|ddd` 选择结构化事务脚本或 DDD 实现。

## 项目边界

本项目实现课程要求的 RBAC3 核心能力，并提供两套可切换的 Service 实现。

- `backend/src/main/java/com/polyauth/api`：共享 Controller、DTO/VO、异常映射、认证与过滤器。
- `backend/src/main/java/com/polyauth/service/port`：两套实现共同遵守的用例接口与结果模型。
- `backend/src/main/java/com/polyauth/service/structured`：结构化事务脚本实现。
- `backend/src/main/java/com/polyauth/service/ddd`：DDD 应用服务与领域模型实现。
- `api/openapi.yaml`：两套实现共用的唯一 API 契约源，`api/openapi.json` 为对应生成物。
- 两套实现不各自创建 Controller、错误响应或 SQL；共享数据库基线、API 契约、错误码和黑盒契约测试。

- “能访问哪个菜单、页面和按钮”由角色与前端权限树决定；“能调用哪个后端 API”再由 API 到权限的映射独立校验。
- 组织机构同时作为账号归属主数据和分公司、部门、项目范围的映射依据。
- 角色支持功能角色与岗位型数据角色，均支持继承；互斥角色不能同时授予同一用户。

明确不包含的能力包括：ABAC、审批流、跨系统 SSO 跳转、刷新令牌、权限变更推送、明文敏感信息导出。

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.4.5、Maven 单模块 |
| 持久层 | Spring Data JPA、MySQL 8.0 |
| 缓存与幂等 | Redis、Spring Data Redis |
| 安全 | JWT、PBKDF2、AES-256-GCM、HMAC-SHA256 |
| 数据源 | Druid |
| 校验与工具 | Spring Validation、Lombok |
| 对象映射 | CloneFactory 编译期注解处理器 |
| 构建 | Maven |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Element Plus |

## 已实现能力

以下能力基线同时适用于 `structured` 与 `ddd` 两种策略；两者共用同一套 Controller、HTTP 路径、请求参数、响应结构、错误码和数据库基线。

### 身份认证与账号

- 账号密码登录、令牌签发、登出和令牌校验。
- 已实现课程项目管理系统所需的 8 个统一身份鉴权接口，JWT 使用统一身份 `sub` 和目标系统 `aud`。
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
- 统一身份按 `operationId` 查询单个 API 的全局权限，管理后台仍可读取前端权限主键集合。

### 授权与安全

- 用户角色授予、撤销和查询，支持授权有效期。
- 互斥角色授予冲突拒绝；授权、回收、角色权限绑定和角色互斥写操作使用 Redis 分布式锁。
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
- 前端每次通过侧栏、顶栏标签页或浏览器历史切换业务菜单时，路由守卫统一调用 `GET /auth/permissions/{permission}`，后端经 `RequestIdFilter`、`AuthInterceptor`、`IdentityAuthController` 与 `LayerLoggingAspect` 输出包含菜单权限码与请求路径的 Logback 日志（控制台 + 滚动文件）及审计记录。
- 权限缓存使用版本号失效，不使用 Redis `KEYS` 扫描。
- 分布式锁使用 owner token 与原子解锁。

## 架构

```text
┌───────────────────────────────────────────────┐
│ web：Vue 管理后台，只依赖统一 HTTP API        │
├───────────────────────────────────────────────┤
│ api：共享 Controller / DTO / VO / 安全与异常   │
├───────────────────────────────────────────────┤
│ service.port：共享用例接口与结果模型          │
├──────────────────────┬────────────────────────┤
│ service.structured   │ service.ddd            │
│ 事务脚本实现         │ 应用服务 + 领域模型    │
├──────────────────────┴────────────────────────┤
│ infrastructure：共享 JPA / 缓存 / 加密 / 日志  │
└───────────────────────────────────────────────┘
```

核心目录：

```text
PolyAuth/
├── api/                          OpenAPI 契约（openapi.yaml / openapi.json）
├── backend/                      单 Spring Boot 模块，双 Service 实现
│   ├── pom.xml
│   ├── src/main/java/com/polyauth/
│   │   ├── api/                  共享接口层
│   │   ├── service/port/         共享用例契约
│   │   ├── service/structured/   结构化实现
│   │   ├── service/ddd/          DDD 实现
│   │   └── infrastructure/       共享基础设施
│   └── src/main/resources/db/    共享 schema、seed 与升级脚本
├── deploy/                       Dockerfile、Swarm stack 与 Nginx 配置
├── docs/                         需求、详细设计、API 手册与部署文档
├── prototype/                    原型与 OpenAPI 校验/推送脚本
└── web/                          Vue 管理后台
```

DDD 分层约束：

```text
api → service.port
service.structured → service.port + infrastructure
service.ddd → service.port + service.ddd.domain + infrastructure
infrastructure → 共享 JPA / 缓存 / 加密 / 外部适配
```

`domain` 与 `application` 不引用 JPA Entity、Spring Data Repository、Redis 或 infrastructure。

数据库核心模型为：

```text
多系统 1:N 前端权限树 1:N 后端 API
用户 M:N 角色 M:N 前端权限
分层组织 1:N 用户
```

普通操作与权限变更统一保存在 `iam_log`；统一日志、令牌等技术支撑表不属于 RBAC 核心 E-R 图。角色互斥由 `iam_role_exclusion` 建模。

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
mysql -u root -p iam_platform < backend/src/main/resources/db/schema.sql
mysql -u root -p iam_platform < backend/src/main/resources/db/seed.sql
```

角色多继承需要新表 `iam_role_parent`：已有数据库请执行
`backend/src/main/resources/db/multi_role_inheritance_upgrade.sql`，该脚本会把旧的
`iam_role.parent_id` 迁移为继承边，可重复执行。

所有角色内置“数据查看”权限（`DATA_VIEW`）；已有数据库请执行
`backend/src/main/resources/db/builtin_data_view_permission.sql`，脚本会登记权限并自动授予全部角色。
该权限不在角色权限组件中展示，也不能通过解绑接口取消。

拥有 `DATA_VIEW` 的用户自动获得目标系统内全部 `action=VIEW` 的权限：所有菜单均可见，
GET/查询类按钮可用；新建、修改、删除、授权、撤销等 `action=ALL` 的权限不会授予，
对应按钮在前端隐藏，对应写接口在后端返回 403。

已部署 RBAC0/RBAC1 数据库升级时，先执行 `backend/src/main/resources/db/rbac3_upgrade.sql`，再重启应用。

已初始化数据库接入课程项目管理系统时，再执行 `backend/src/main/resources/db/identity_course_upgrade.sql`。
该脚本幂等登记 `course-project-management` 系统、`teacher / student / admin` 角色和 `TEC` 权限。

初始化管理员为 `admin / Admin@123`。该密码仅用于本地初始化，部署前必须修改。

MySQL 建议设置 `default-time-zone = '+08:00'`；`seed.sql` 已在会话级设置北京时间。

### 关键环境变量

| 变量 | 默认值或用途 |
| --- | --- |
| `DB_HOST` / `DB_PORT` | MySQL 地址与端口 |
| `DB_NAME` | 数据库名，默认 `iam_platform`（两种策略共用） |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号与密码 |
| `DB_SSL` | MySQL SSL 开关，默认 `false` |
| `REDIS_HOST` / `REDIS_PORT` | Redis 地址与端口 |
| `REDIS_PASSWORD` | Redis 密码 |
| `JWT_SECRET` | JWT 密钥 |
| `AES_KEY` | 敏感字段加密密钥 |
| `HMAC_KEY` | 检索哈希与签名密钥 |

生产环境必须通过密钥或环境变量注入，不得沿用仓库中的开发默认值。

### 后端

在 `backend/` 目录执行：

```bash
./mvnw clean test
./mvnw -Dspring-boot.run.arguments=--app.backend-strategy=structured spring-boot:run
```

结构化策略地址：`http://localhost:8080/api/v1`。

切换到 DDD 策略（同一 JAR、同一端口、同一数据库）：

```bash
./mvnw -Dspring-boot.run.arguments=--app.backend-strategy=ddd spring-boot:run
```

两种策略共用 `iam_platform` 的 schema 和 seed；启动前执行 `backend/src/main/resources/db/schema.sql` 与 `seed.sql`。

双策略契约验证：

```powershell
.\run-dual-contracts.ps1
```

### 前端

```bash
cd web
pnpm install
pnpm dev
pnpm typecheck
pnpm build
```

前端通过 Vite 代理访问真实后端；不包含 Mock 插件、Mock 数据或 Mock 开关。

### Docker Swarm

正式部署采用 8 台 2 核 2G 节点：3 台 manager、3 台 `structured` 策略应用、1 台数据库、1 台 `ddd` 策略应用。两套应用使用同一个 JAR，仅 `APP_BACKEND_STRATEGY` 不同。完整标签、构建、密钥、数据库初始化、端口和菜单日志验收步骤见 [docs/deployment-8nodes.md](docs/deployment-8nodes.md)。

## 鉴权规则

- `POST /auth/login` 为公开接口。
- 统一身份契约见 [docs/07_统一身份外部契约.md](docs/07_统一身份外部契约.md)；该组 8 个接口直接返回业务对象和字符串错误码，不使用内部 `Result` 包装。
- 登录响应的 `userId` 是本地数据库主键字符串；统一身份用户标识使用 JWT `sub` 或 `/auth/me.subject`，值为 `user_no`。
- 管理端请求携带 `Authorization: Bearer <token>`。
- 管理端请求必须先匹配已登记的 `iam_api`，再检查该 API 映射的前端权限；任一环节缺失都默认拒绝。
- 开放 API 可携带 `X-App-Id`、`X-Timestamp`、`X-Nonce`、`X-Signature` 进行接入方认证。
- OpenAPI 普通用户令牌只能查询本人；接入方签名可查询授权范围内的用户。

## API 概览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/auth/login` | 统一身份登录，签发带 `aud` 的 JWT |
| POST | `/api/v1/auth/logout` | 吊销当前令牌，成功返回 204 |
| GET | `/api/v1/auth/me` | 当前用户、角色和前端权限码 |
| GET | `/api/v1/auth/permissions/{permission}` | 校验单个前端权限码 |
| GET | `/api/v1/auth/departments` | 按层级分页查询统一身份部门 |
| GET | `/api/v1/auth/api-permissions/{operationId}` | 按 operationId 校验 API 调用权限 |
| GET | `/api/v1/auth/departments/{departmentId}/users` | 分页查询部门用户 |
| GET | `/api/v1/auth/users/{userId}` | 按统一身份标识查询用户 |
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
| PUT | `/api/v1/roles/{roleId}` | JSON 修改角色、状态和父角色 |
| GET/POST | `/api/v1/roles/{roleId}/exclusions` | 查询 / 新建角色互斥 |
| DELETE | `/api/v1/roles/{roleId}/exclusions/{excludedRoleId}` | 解除角色互斥 |
| POST | `/api/v1/roles/{roleId}/assignments` | 将角色分配给用户 |
| DELETE | `/api/v1/roles/{roleId}/assignments/{userId}` | 撤销用户角色 |
| GET | `/api/v1/roles/{roleId}/assignments` | 查询角色下的用户分配 |
| GET | `/api/v1/roles/assignments` | 按用户查询角色分配 |
| POST/DELETE/GET | `/api/v1/roles/{roleId}/permissions` | 角色权限授予 / 解除 / 查询 |
| GET/POST | `/api/v1/systems` | 子系统列表 / 登记 |
| GET | `/api/v1/systems/{systemId}` | 系统详情 |
| GET/POST | `/api/v1/permissions` | 前端权限树查询 / 登记 |
| POST/DELETE | `/api/v1/permissions/{permissionId}/apis/{apiId}` | 绑定 / 解除 API 映射 |
| GET | `/api/v1/permissions/{permissionId}/apis` | 查询权限下的 API |
| GET/POST | `/api/v1/apis` | 后端 API 列表 / 登记 |
| GET | `/api/v1/apis/{apiId}` | 后端 API 详情 |
| GET | `/api/v1/open/permissions` | 管理后台读取用户前端权限主键集合 |
| GET | `/api/v1/logs` | 统一日志分页查询（普通操作 + 权限变更） |
| GET | `/api/v1/logs/export` | 导出统一日志 CSV |

## 验证状态

合并后的 `backend` 模块共 184 项测试通过，覆盖结构化 Service、DDD 用例与领域模型、共享 API 层、JWT/统一身份、Controller 契约、JPA Adapter、日志与架构约束。`structured` 与 `ddd` 两种策略分别启动的真实 HTTP 烟测均通过。

前端 29 项 Vitest 测试通过，`pnpm typecheck`、`pnpm build` 和 `pnpm lint` 均通过。

`backend/src/test/java/com/polyauth/contract` 共 16 个共享黑盒场景；未指定 `contract.baseUrl` 时会明确跳过，不伪造通过。要确认两种策略等价，必须分别以 `structured` 与 `ddd` 启动后端并执行契约脚本。

两套实现共用同一份 `service.port`，结构化与 DDD 均覆盖 52 个功能权限端点。真实契约联调仍需在运行中的服务上执行，不能用自动跳过结果替代。

`docs/` 目录保留课程详细设计材料；功能范围以本 README、当前代码和
[API 使用手册](docs/API使用手册.md) 为准。

# PolyAuth：多系统统一权限管理平台

PolyAuth 是面向多业务系统的统一身份与 RBAC3 权限平台，集中管理系统、前端菜单/按钮权限、后端 API、账号、组织、角色继承、角色互斥、数据范围和审计。项目采用 Maven 多模块，同时维护**结构化后端**与**面向对象后端**，通过共享 HTTP 契约验证外部行为等价。

## 项目边界

本项目实现课程要求的 RBAC3 核心能力，并持续迁移为可独立运行的双后端系统。

- `structured-backend`：保留完整管理能力，按“校验 → 处理 → 持久化”组织 Service。
- `oop-backend`：当前完成本地认证、角色生命周期、角色授权、权限解析和默认拒绝的 OOP 纵向切片，继续扩展组织、数据权限和系统管理。
- 两套后端不互相依赖，不共享业务实现；共享数据库基线、API 路径、错误码和黑盒契约测试。

- “能访问哪个菜单、页面和按钮”由角色与前端权限树决定；“能调用哪个后端 API”再由 API 到权限的映射独立校验。
- “能查看哪个分公司、部门、项目或本人的数据”由角色数据范围与目标组织/项目/用户联合判定。
- 组织机构同时作为账号归属主数据和分公司、部门、项目范围的映射依据。
- 角色支持功能角色与岗位型数据角色，均支持继承；互斥角色不能同时授予同一用户。

明确不包含的能力包括：ABAC、审批流、跨系统 SSO 跳转、刷新令牌、权限变更推送、明文敏感信息导出。

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 双后端 | Java 17、Spring Boot 3.4.5、Maven 多模块 |
| 持久层 | Spring Data JPA、MySQL 8.0 |
| 缓存与幂等 | Redis、Spring Data Redis |
| 安全 | JWT、PBKDF2、AES-256-GCM、HMAC-SHA256 |
| 数据源 | Druid |
| 校验与工具 | Spring Validation、Lombok |
| 对象映射 | CloneFactory 编译期注解处理器 |
| 构建 | Maven |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Element Plus |

## 已实现能力

以下为结构化后端的完整能力基线；OOP 后端当前实现的纵向切片在“架构”和“验证状态”中单独说明。

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
┌────────────────────────────────────────────────────┐
│ web：Vue 管理后台，只依赖统一 HTTP API             │
├────────────────────────────────────────────────────┤
│ contract-tests：共享黑盒场景、HTTP 客户端、fixture │
├──────────────────────────────┬─────────────────────┤
│ structured-backend           │ oop-backend         │
│ interfaces → application     │ interfaces          │
│              → infrastructure│   → application     │
│                              │       → domain      │
│                              │ infrastructure      │
│                              │   → domain ports    │
└──────────────────────────────┴─────────────────────┘
```

核心目录：

```text
PolyAuth/
├── pom.xml                       Maven 聚合与共享版本
├── structured-backend/           完整结构化实现，端口 8080
├── oop-backend/                  独立 OOP 实现，端口 8081
├── contract-tests/               不依赖生产类的 HTTP 契约
├── db/                           共享 schema、seed 与升级脚本
├── web/                          Vue 管理后台
├── design/                       详细设计与接口材料
└── scripts/                      双后端契约验证脚本
```

OOP 分层约束：

```text
interface → application/command/usecase → domain/model/service/repository
                      ↘ application/port
infrastructure → domain + application/port
```

`domain` 与 `application` 不引用 JPA Entity、Spring Data Repository、Redis 或 infrastructure。

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
mysql -u root -p -e "CREATE DATABASE iam_platform_oop DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -u root -p iam_platform < db/schema.sql
mysql -u root -p iam_platform < db/seed.sql
mysql -u root -p iam_platform_oop < db/schema.sql
mysql -u root -p iam_platform_oop < db/seed.sql
```

已部署 RBAC0/RBAC1 数据库升级时，先执行 `db/rbac3_upgrade.sql`，再重启应用。

已初始化数据库接入课程项目管理系统时，再执行 `db/identity_course_upgrade.sql`。
该脚本幂等登记 `course-project-management` 系统、`teacher / student / admin` 角色和 `TEC` 权限。

初始化管理员为 `admin / Admin@123`。该密码仅用于本地初始化，部署前必须修改。

MySQL 建议设置 `default-time-zone = '+08:00'`；`seed.sql` 已在会话级设置北京时间。

### 关键环境变量

| 变量 | 默认值或用途 |
| --- | --- |
| `DB_HOST` / `DB_PORT` | MySQL 地址与端口 |
| `DB_NAME` | 结构化默认 `iam_platform`，OOP 默认 `iam_platform_oop` |
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
.\mvnw.cmd clean test
.\mvnw.cmd -pl structured-backend spring-boot:run
```

结构化后端地址：`http://localhost:8080/api/v1`。

OOP 后端启动：

```bash
.\mvnw.cmd -pl oop-backend spring-boot:run
```

OOP 后端地址：`http://localhost:8081/api/v1`。两套数据库使用同一 schema 和 seed，但建议使用独立库，避免并行验证时互相修改数据。

双后端契约验证：

```powershell
.\scripts\run-dual-contracts.ps1
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

## 鉴权规则

- `POST /auth/login` 为公开接口。
- 统一身份契约见 [design/07_统一身份外部契约.md](design/07_统一身份外部契约.md)；该组 8 个接口直接返回业务对象和字符串错误码，不使用内部 `Result` 包装。
- 管理端请求携带 `Authorization: Bearer <token>`。
- 管理端请求必须先匹配已登记的 `iam_api`，再检查该 API 映射的前端权限；任一环节缺失都默认拒绝。
- 数据权限开放接口可携带 `X-App-Id`、`X-Timestamp`、`X-Nonce`、`X-Signature` 进行接入方认证。
- OpenAPI 普通用户令牌只能查询本人；接入方签名可查询授权范围内的用户。
- `/open/authorize/resource` 同时接收功能 API 与目标组织、项目或用户上下文，功能权限和数据范围都通过才放行。

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
| GET | `/api/v1/open/permissions` | 管理后台读取用户前端权限主键集合 |
| GET | `/api/v1/open/data-scopes` | 查询用户当前生效的数据范围 |
| POST | `/api/v1/open/authorize/resource` | 功能权限与数据权限联合判定 |
| GET/POST | `/api/v1/systems/data-scopes` | 数据范围字典查询 / 新建 |
| GET | `/api/v1/logs` | 统一日志分页查询（普通操作 + 权限变更） |
| GET | `/api/v1/logs/export` | 导出统一日志 CSV |

## 验证状态

结构化后端 120 项测试通过，覆盖 CloneFactory、统一身份、JWT、账号、组织、角色继承/互斥、数据权限、权限树、日志、校验和幂等。OOP 后端 22 项测试通过，覆盖角色不变量、继承展开、授权有效期、权限解析、用例编排、本地认证、JPA Adapter 和完整容器装配。

前端 19 项 Vitest 测试通过，`pnpm typecheck`、`pnpm build` 和 `pnpm lint` 均通过。

`contract-tests` 共 6 个共享黑盒场景；未指定 `contract.baseUrl` 时会明确跳过，不伪造通过。要确认两套实现等价，必须分别启动 8080、8081 并执行双后端契约脚本。

当前 OOP 尚未迁移组织管理、数据权限、角色互斥、系统/API 管理、完整统一身份查询和管理端审计查询，因此不能标记为全功能等价；迁移状态见 `contract-tests/README.md`。

`design/` 目录保留课程详细设计材料；功能范围以本 README 和当前代码为准。

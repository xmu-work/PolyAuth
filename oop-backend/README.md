# PolyAuth OOP Backend

端口：`8081`

API 根路径：`/api/v1`

当前实现范围与结构化后端外部 API 对齐：

- 本地账号密码登录、JWT 签发/校验/吊销和服务端令牌会话。
- 自助修改密码和完整统一身份查询契约。
- 用户账号创建、分页、详情、敏感视图、状态变更和调岗。
- 组织树、创建、详情、下级、移动、修改和停用。
- 系统、前端权限树、后端 API 目录及权限映射。
- 角色创建、查询、更新、继承成环检测。
- 用户角色授予、撤销和有效期控制。
- 角色前端权限绑定、解绑、查询及纯函数权限解析。
- 角色互斥和功能权限判定。
- API 目录与权限映射读取、`operationId` 权限判断和默认拒绝守卫。
- 开放 API Bearer/HMAC 双模式认证、nonce 防重放和功能权限集合查询。
- 统一审计查询、CSV 导出和写请求幂等。
- 登录及管理请求只追加审计。

分层：

```text
interfaces
  → application/command + application/usecase
  → domain/model + domain/service + domain/repository

infrastructure
  → domain + application/port
```

硬边界：

- `domain` 不引用 Spring、JPA、Jackson 或 interfaces。
- `application` 不引用 JPA Entity、Spring Data Repository 或 infrastructure。
- `infrastructure/persistence/entity` 与领域对象通过显式 Mapper 转换。
- 角色和授权状态只通过领域方法改变。
- `application` 不依赖 Spring Data Repository、JPA Entity 或 infrastructure。

启动：

```bash
.\mvnw.cmd -pl oop-backend spring-boot:run
```

数据库默认名 `iam_platform`（与结构化后端共用同一数据库），可执行根目录 `db/schema.sql` 与 `db/seed.sql` 初始化。

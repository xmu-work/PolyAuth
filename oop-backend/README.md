# PolyAuth OOP Backend

端口：`8081`

API 根路径：`/api/v1`

当前纵向切片：

- 本地账号密码登录、JWT 签发/校验/吊销和服务端令牌会话。
- 角色创建、查询、更新、继承成环检测。
- 用户角色授予、撤销和有效期控制。
- 角色前端权限绑定与权限集合纯函数解析。
- API 登记、权限映射和默认拒绝守卫。
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

启动：

```bash
.\mvnw.cmd -pl oop-backend spring-boot:run
```

数据库默认名 `iam_platform_oop`，可执行根目录 `db/schema.sql` 与 `db/seed.sql` 初始化。

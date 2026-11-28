# PolyAuth Backend

同应用、同 API、同数据库、双 Service 实现：

```text
backend/
├── pom.xml
├── src/main/java/com/polyauth/
│   ├── PolyAuthApplication.java
│   ├── api/                    共享 Controller、DTO/VO、异常、安全与过滤器
│   ├── service/
│   │   ├── port/               共享用例接口与结果模型
│   │   ├── common/             共享枚举与业务异常
│   │   ├── structured/         结构化事务脚本实现
│   │   └── ddd/                DDD 应用服务与领域模型
│   └── infrastructure/         共享 JPA、缓存、加密、日志、调度和外部适配
├── src/main/resources/
│   ├── application.yml
│   ├── logback-spring.xml
│   └── db/                     共享 schema、seed 与升级脚本
└── src/test/java/com/polyauth/ 单元测试与 HTTP 黑盒契约测试
```

## 选择实现

启动时通过配置二选一：

```yaml
app:
  backend-strategy: structured   # 或 ddd
```

- `structured`：默认值，事务脚本实现。
- `ddd`：领域驱动实现。
- 两套实现共用 Controller、`service.port` 接口、JPA 表结构与 OpenAPI 契约，差异只在 Service 内部。

## 构建与启动

```bash
./mvnw test
./mvnw -DskipTests package
./mvnw -Dspring-boot.run.arguments=--app.backend-strategy=ddd spring-boot:run
```

数据库默认 `iam_platform`；首次启动前执行 `src/main/resources/db/schema.sql` 与 `seed.sql`。
已有数据库升级角色多继承时执行 `src/main/resources/db/multi_role_inheritance_upgrade.sql`；
升级内置“数据查看”权限时执行 `src/main/resources/db/builtin_data_view_permission.sql`。

角色编码 `role_code` 仍作为数据库内部唯一标识，但新建角色不再要求前端填写；省略时后端自动生成。
角色权限组件不展示 `DATA_VIEW`，后端也会拒绝解绑该内置权限。

`DATA_VIEW` 会隐式展开为目标系统全部 `action=VIEW` 的权限（菜单 + 只读按钮）；
`action=ALL` 的写、删除、授权权限不会授予，前后端分别通过隐藏按钮和 403 拒绝。

## 双策略契约验证

先分别以两种策略启动后端，再运行：

```bash
./mvnw -Dtest=DualBackendContractTest -Dcontract.baseUrl=http://localhost:8080/api/v1 test
```

也可在 Windows 下执行 `run-dual-contracts.ps1`。

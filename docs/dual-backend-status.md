# PolyAuth 双实现迁移状态

同一 Spring Boot 应用通过 `app.backend-strategy=structured|ddd` 选择 Service 实现。

| 能力 | structured | ddd | 共享 HTTP 契约 |
| --- | --- | --- | --- |
| 本地登录、JWT、登出 | 完整 | 已实现 | 已覆盖 |
| 角色创建/查询/更新 | 完整 | 已实现 | 已覆盖 |
| 角色继承成环 | 已实现 | 已实现 | 已覆盖 |
| 用户角色授予/撤销/有效期 | 完整 | 已实现 | 过期边界已覆盖 |
| 角色权限绑定与继承解析 | 完整 | 已实现 | 解析单测已覆盖 |
| API 登记、目录读取与默认拒绝 | 完整 | 已实现 | 目录读取场景已覆盖 |
| 角色互斥 | 完整 | 已实现 | 单元规则已覆盖 |
| 组织管理 | 完整 | 已实现 | 组织树 HTTP 场景已覆盖 |
| 系统/权限树管理 | 完整 | 已实现 | 列表 HTTP 场景已覆盖 |
| 统一身份完整 8 接口 | 完整 | 已实现 | `/auth/me` 场景已覆盖 |
| 审计查询/导出 | 完整 | 已实现 | 日志分页场景已覆盖 |

`ApiParityContractTest` 校验两套实现覆盖相同的 52 个功能权限端点。共享黑盒测试共 16 个场景；
发布前仍需分别以两种策略启动后端并完成真实 MySQL 联调。

两种实现共享 `api` 层、`service.port` 接口、同一份 SQL 基线、外部 API 语义与黑盒场景；
差异只存在于 `service.structured` 与 `service.ddd` 内部。

## 验证命令

```powershell
cd backend
.\mvnw.cmd clean test
.\run-dual-contracts.ps1
```

第二条命令要求分别以 `structured` 与 `ddd` 启动后端，并使用相同 schema/seed 初始化 `iam_platform`。

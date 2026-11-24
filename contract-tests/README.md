# PolyAuth 双后端契约测试

契约测试只通过 HTTP 调用目标后端，不依赖任一模块的生产代码。

先启动目标后端，再执行：

```powershell
.\mvnw.cmd -pl contract-tests test -Dcontract.baseUrl=http://localhost:8080/api/v1
.\mvnw.cmd -pl contract-tests test -Dcontract.baseUrl=http://localhost:8081/api/v1
```

默认登录账号可通过以下属性覆盖：

```text
-Dcontract.username=admin
-Dcontract.password=Admin@123
-Dcontract.system=ADMIN
```

当前共有 16 个共享 HTTP 场景，覆盖角色、授权、继承、本地认证、令牌校验、
`operationId` 错误码、当前身份、组织树、系统、权限树、API 目录、用户分页、
统一日志分页以及统一身份外部审计日志幂等读写。

OOP 的 `ApiParityContractTest` 还静态校验其注册了与结构化后端相同的 52 个功能权限端点，
并明确拒绝已下线能力端点。
真实发布前仍必须分别在 8080、8081 启动两套后端并执行本模块测试，未运行或跳过的结果
不能替代真实双后端验收。

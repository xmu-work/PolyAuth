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

当前 OOP 纵向切片已覆盖角色、授权、权限解析和本地认证。数据权限、组织管理、
系统与 API 管理等完整契约矩阵尚在迁移中，未通过前不能把 OOP 后端标记为全功能等价。

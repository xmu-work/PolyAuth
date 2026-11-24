# PolyAuth 与课程项目管理系统（course-project-mgt）前后端联调指南

## 1. 统一身份基础地址配置

PolyAuth 后端（`structured-backend` 与 `oop-backend`）均已通过 `server.servlet.context-path: /api/v1` 暴露 8 个统一身份接口（`/api/v1/auth/*`），且由前端 Nginx（`nginx-default.conf`）将 `/api/` 原样转发至 `app:8080`。

- **课程前端 `web/.env.production`**（构建时变量，修改后须重新 `npm run build` 并重建前端镜像）：
  ```env
  VITE_API_BASE_URL=/api
  VITE_MOCK_ENABLED=false
  VITE_IDENTITY_BASE_URL=http://<IAM对外地址>/api/v1
  ```
  > 若改用 OOP 后端（8082 端口）直连联调，则填 `http://<OOP节点地址>:8082/api/v1`。

- **课程后端 `deploy/.env`**（运行时变量）：
  - 同 Swarm 集群（挂载了 `javaee-proxy` 覆盖配置）：
    ```env
    IDENTITY_BASE_URL=http://polyauth-app:8080/api/v1
    ```
    （对接 OOP 后端则使用 `http://polyauth-oop:8080/api/v1`）
  - 跨机器/公网地址访问：
    ```env
    IDENTITY_BASE_URL=http://<IAM对外地址>/api/v1
    ```

## 2. CORS 跨域配置

PolyAuth 双后端默认通过环境变量 `CORS_ALLOWED_ORIGINS` 控制跨域（默认 `*` 方便联调，允许 `GET/POST/PUT/DELETE/PATCH/OPTIONS` 与 `Authorization` 请求头，并自动放行 `OPTIONS` 预检请求）。

生产联调完成后建议收缩为课程前端的具体 Origin（支持逗号分隔，自动去除末尾 `/`）：

```sh
export CORS_ALLOWED_ORIGINS="http://<课程前端域名或IP>"
```

## 3. 数据库初始化与 10000 员工批量授角

在 IAM 数据库（结构化后端为 `iam_platform`，OOP 后端为 `iam_platform_oop`）依次执行：

1. 全新库先执行 `db/schema.sql` 与 `db/seed.sql`；已有库跳过。
2. 执行幂等升级脚本 `db/identity_course_upgrade.sql`：
   - 登记 `course-project-management` 系统；
   - 初始化课程全部前端权限码（`MCO`～`MTEL`）、全局 API 权限码（`TC`～`UL`）及基础访问权限 `COURSE_MEMBER`；
   - 初始化 `iam_api` 中的课程 OpenAPI `operationId` 映射；
   - 初始化 `admin` / `teacher` / `student` 角色并绑定权限。
3. 将市政公司 10000 名员工与组织架构导入 `iam_org` / `iam_user`（确保 `iam_user.user_no` 与课程库工号/学号一致）。
4. 执行幂等批量授角脚本 `db/grant_course_roles_by_org.sql`：
   - 总公司（`HQ`）及直属部门在职员工 → 自动授予 `teacher`；
   - 分公司（`BRANCH`）及下级在职员工 → 自动授予 `student`。
5. 为需要进入课程管理后台的教师单独追加 `admin` 角色（示例，将 `SG000001` 替换为实际工号）：
   ```sql
   INSERT INTO iam_user_role (user_id, role_id, effective_time, grant_reason, status)
   SELECT u.id, r.id, NOW(), '课程系统管理员', 'ACTIVE'
   FROM iam_user u
   JOIN iam_role r ON r.role_code = 'admin'
   WHERE u.user_no = 'SG000001' AND u.is_deleted = 0
   ON DUPLICATE KEY UPDATE status = 'ACTIVE', expire_time = NULL;
   ```

## 4. 联调部署与验收检查

使用覆写文件接入 `javaee-proxy` 网络部署 IAM：

```sh
docker network inspect javaee-proxy >/dev/null 2>&1 || \
  docker network create --driver overlay --attachable javaee-proxy

docker stack deploy -c deploy/swarm/iam-stack.yml \
  -c deploy/swarm/iam-course-integration.override.yml \
  --with-registry-auth iam
```

部署后按以下顺序验证（将 `<IAM_BASE>` 替换为 `http://<IAM对外地址>/api/v1`，`<COURSE_ORIGIN>` 替换为课程前端 Origin）：

```sh
# 1. 验证 CORS 预检（应返回 200 且含 Access-Control-Allow-Origin / Authorization）
curl -i -X OPTIONS \
  -H "Origin: <COURSE_ORIGIN>" \
  -H "Access-Control-Request-Method: GET" \
  -H "Access-Control-Request-Headers: Authorization" \
  "<IAM_BASE>/auth/permissions/MCO"

# 2. 验证统一身份登录（使用课程管理员或教师账号）
TOKEN=$(curl -fsS -X POST -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<密码>","systems":["course-project-management"]}' \
  "<IAM_BASE>/auth/login" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

# 3. 验证 /auth/me、/auth/permissions/MCO、/auth/departments
curl -i -H "Origin: <COURSE_ORIGIN>" -H "Authorization: Bearer $TOKEN" "<IAM_BASE>/auth/me"
curl -i -H "Origin: <COURSE_ORIGIN>" -H "Authorization: Bearer $TOKEN" "<IAM_BASE>/auth/permissions/MCO"
curl -i -H "Origin: <COURSE_ORIGIN>" -H "Authorization: Bearer $TOKEN" "<IAM_BASE>/auth/departments"
```

# PolyAuth 正式部署手册：8 台 2 核 2G + Docker Swarm

本文是权限系统的正式部署方案。8 台机器全部为 **2 核 CPU、2GB 内存**，使用 Docker Swarm 运行权限系统前端、结构化后端、OOP 后端、MySQL 和 Redis。

## 1. 节点规划

| 节点 | Swarm 角色 | 标签 | 主要服务 | 内存预算 |
| --- | --- | --- | --- | --- |
| `control-01` | manager | 无 | `iam-web` | 前端 128M + 系统 |
| `control-02` | manager | 无 | `iam-web` | 前端 128M + 系统 |
| `control-03` | manager | 无 | `iam-web` | 前端 128M + 系统 |
| `app-01` | worker | `role=app` | `iam-app` ×1 | JVM 上限 1280M |
| `app-02` | worker | `role=app` | `iam-app` ×1 | JVM 上限 1280M |
| `app-03` | worker | `role=app` | `iam-app` ×1 | JVM 上限 1280M |
| `data-01` | worker | `role=db` | MySQL + Redis | 900M + 256M |
| `oop-01` | worker | `role=oop` | `iam-oop` ×1 | JVM 上限 1280M |

拓扑为：

```text
3 manager：Swarm 高可用仲裁 + web(global)
3 app worker：结构化后端 3 副本
1 db worker：MySQL + Redis
1 oop worker：DDD/OOP 后端 1 副本
```

每个 2G 节点只运行一个 JVM。`iam-app` 和 `iam-oop` 都必须使用 `stop-first` 更新，避免滚动更新时同一节点短暂运行两个 JVM。

## 2. 初始化 Swarm 与标签

选择 `control-01` 初始化 manager：

```bash
docker swarm init --advertise-addr <control-01-IP>
```

把 `control-02`、`control-03` 加入为 manager，把其余 5 台加入为 worker。完成后必须有 3 个 manager，避免重启时丢失仲裁。

在任一 manager 上执行：

```bash
docker node ls

docker node update --label-add role=app app-01
docker node update --label-add role=app app-02
docker node update --label-add role=app app-03
docker node update --label-add role=db data-01
docker node update --label-add role=oop oop-01
```

标签名称必须与节点主机名完全一致。检查：

```bash
docker node inspect app-01 --format '{{ json .Spec.Labels }}'
docker node inspect data-01 --format '{{ json .Spec.Labels }}'
docker node inspect oop-01 --format '{{ json .Spec.Labels }}'
```

创建 overlay 网络：

```bash
docker network create --driver overlay iam-net
```

在 `data-01` 上创建持久化目录：

```bash
sudo mkdir -p /srv/iam/mysql /srv/iam/redis
sudo chown -R 999:999 /srv/iam/mysql
sudo chown -R 999:999 /srv/iam/redis
```

MySQL 和 Redis 绑定本地目录，不能漂移到其他节点。该节点故障后需要人工恢复数据盘，这是当前单数据库节点的已知限制。

## 3. 构建和推送镜像

在构建机或暂时空闲的 manager 上登录 SWR：

```bash
docker login swr.cn-north-4.myhuaweicloud.com
```

在仓库根目录执行：

```bash
export REG=swr.cn-north-4.myhuaweicloud.com/ooad-mytx

docker build -f deploy/Dockerfile.app -t "$REG/iam-app:1.0.1" .
docker build -f deploy/Dockerfile.oop -t "$REG/iam-oop-app:1.0.1" .
docker build -f deploy/Dockerfile.web -t "$REG/iam-web:1.0.1" .

docker push "$REG/iam-app:1.0.1"
docker push "$REG/iam-oop-app:1.0.1"
docker push "$REG/iam-web:1.0.1"
```

MySQL 和 Redis 基础镜像也应转推到同一 SWR 组织，避免集群节点访问 Docker Hub：

```bash
docker pull mysql:8.0
docker tag mysql:8.0 "$REG/mysql:8.0"
docker push "$REG/mysql:8.0"

docker pull redis:7-alpine
docker tag redis:7-alpine "$REG/redis:7-alpine"
docker push "$REG/redis:7-alpine"
```

镜像名和标签必须与 `deploy/iam-stack.yml` 完全一致。

## 4. 创建 secrets

只能在 manager 上执行，且只在首次部署时创建。

```bash
JWT_SECRET="$(openssl rand -base64 48 | tr -d '\n')"
HMAC_KEY="$(openssl rand -hex 32)"
DB_APP_PASSWORD="$(openssl rand -hex 24)"
MYSQL_ROOT_PASSWORD="$(openssl rand -hex 24)"
REDIS_PASSWORD="$(openssl rand -hex 24)"
IAM_DEFAULT_PASSWORD='替换为创建用户的初始强密码'

printf '%s' "$JWT_SECRET"          | docker secret create jwt_secret -
printf '%s' 'iam-aes-256-key-please-change-in-production' | docker secret create crypto_aes_key -
printf '%s' "$HMAC_KEY"            | docker secret create hmac_key -
printf '%s' "$DB_APP_PASSWORD"     | docker secret create db_app_password -
printf '%s' "$MYSQL_ROOT_PASSWORD" | docker secret create mysql_root_password -
printf '%s' "$REDIS_PASSWORD"      | docker secret create redis_password -
printf '%s' "$IAM_DEFAULT_PASSWORD" | docker secret create iam_default_password -

docker secret ls
```

必须存在 7 个 secret。`crypto_aes_key` 首次部署必须继续使用仓库默认值，因为 `seed.sql` 中的管理员姓名使用该密钥加密；待默认管理员重建后再统一轮换 AES 密钥。

`deploy/entrypoint.sh` 会把 secret 转成 Spring 环境变量，包括 `HMAC_KEY`。不要在栈文件、源码或日志中直接写密钥。

如果集群仍在运行旧栈，升级前必须先补建 `hmac_key`：

```bash
printf '%s' "$(openssl rand -hex 32)" | docker secret create hmac_key -
```

否则新任务会因为 `/run/secrets/hmac_key` 不存在而无法启动。

## 5. 部署完整栈

在 manager 上进入仓库目录：

```bash
cd deploy
docker stack config -c iam-stack.yml >/tmp/iam-stack.rendered.yml
docker stack deploy -c iam-stack.yml --with-registry-auth iam
```

部署后观察：

```bash
docker stack services iam
docker stack ps iam
```

预期：

- `iam_mysql`：1/1
- `iam_redis`：1/1
- `iam_app`：3/3
- `iam_oop`：1/1
- `iam_web`：3/3，分布在 3 台 manager

如果 `iam_app` 出现两个 Pending，检查是否有且只有三台节点带 `role=app`；如果 `iam_oop` Pending，检查 `oop-01` 的 `role=oop` 标签；如果 `iam_web` 不足三副本，检查三台 manager 是否都能拉取私有镜像。

## 6. 初始化两个数据库

在 `data-01` 上找到 MySQL 容器：

```bash
CID="$(docker ps -qf name=iam_mysql)"
echo "$CID"
```

从仓库所在机器把 SQL 文件传到 `data-01`：

```bash
scp backend/src/main/resources/db/schema.sql backend/src/main/resources/db/seed.sql data-01:/tmp/
```

在 `data-01` 上复制到 MySQL 容器：

```bash
docker cp /tmp/schema.sql "$CID:/tmp/schema.sql"
docker cp /tmp/seed.sql "$CID:/tmp/seed.sql"
```

初始化 `iam_platform` 数据库：

```bash
docker exec "$CID" sh -c \
  'mysql -uroot -p"$(cat /run/secrets/mysql_root_password)" --default-character-set=utf8mb4 iam_platform < /tmp/schema.sql'

docker exec "$CID" sh -c \
  'mysql -uroot -p"$(cat /run/secrets/mysql_root_password)" --default-character-set=utf8mb4 iam_platform < /tmp/seed.sql'
```

两种策略共用 `iam_platform`，不需要再初始化第二套数据库。

若当前数据库已有数据，不要重复执行全量 `schema.sql`；应改用对应的幂等升级脚本。

## 7. 验收权限系统

### 7.1 前端

浏览器访问任一 manager：

```text
http://<manager-IP>/
```

默认管理员为 `admin / Admin@123`，首次部署后必须修改密码。

### 7.2 结构化后端

在前端同源访问 `/api/v1`。也可以在集群网络内验证登录：

```bash
docker run --rm --network iam-net curlimages/curl \
  -s -X POST http://app:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@123","systems":["ADMIN"]}'
```

### 7.3 OOP 后端

OOP 后端发布在 `oop-01` 的 8082 端口：

```text
http://<oop-01-IP>:8082/api/v1
```

防火墙只向检查人员的 IP 开放 8082。OOP 已注册与结构化后端相同的 50 个功能权限端点；发布前必须启动两套后端并执行 15 项共享 HTTP 契约。

## 8. 菜单切换与 Logback 验收

登录后通过左侧菜单、顶部标签页或浏览器前进/后退切换任意业务菜单（含切回「首页」）时，前端全局路由守卫都会先调用：

```text
GET /api/v1/auth/permissions/{permissionCode}
```

后端由 `RequestIdFilter`（`INFO` HTTP 摘要）、`IdentityAuthController.checkPermission`（`INFO` 菜单权限码判定）与 `LayerLoggingAspect`（按目标类记录分层调用耗时）产出 Logback 日志，并同时写入控制台与滚动日志文件（`/app/logs/polyauth-structured.log` / `/app/logs/polyauth-oop.log`）。

- **多副本聚合查看**（推荐在 Manager 节点执行，勿只看单台 App 节点的 `docker logs`）：

```bash
docker service logs -f iam_app --tail 100 | grep -E 'permission-check|/auth/permissions|http method='
```

预期可看到类似内容：

```text
[requestId=...] c.p.s.i.identity.IdentityAuthController - permission-check system=ADMIN userId=1 permission=MENU_HOME allowed=true
[requestId=...] c.p.s.interfaces.RequestIdFilter - http method=GET uri=/api/v1/auth/permissions/MENU_HOME status=200 elapsedMs=3 user=admin system=ADMIN
```

- **查看容器内持久化滚动日志文件**：

```bash
docker exec "$(docker ps -qf name=iam_app | head -n 1)" tail -n 100 /app/logs/polyauth-structured.log
```

- **切换前端至 OOP 后端验收 `iam_oop` 日志**（无需重打前端镜像）：

```bash
docker service update --env-add API_UPSTREAM=http://oop:8080 iam_web
docker service logs -f iam_oop --tail 100 | grep -E 'permission-check|/auth/permissions|http method='
# 验收后切回结构化后端：
docker service update --env-add API_UPSTREAM=http://app:8080 iam_web
```

## 9. 更新和安全清理

滚动更新前端或后端：

```bash
docker service update --image "$REG/iam-app:1.0.1" iam_app
docker service update --image "$REG/iam-oop-app:1.0.1" iam_oop
docker service update --image "$REG/iam-web:1.0.1" iam_web
```

常用排障：

```bash
docker service ps iam_app --no-trunc
docker service logs iam_app --tail 200
docker service logs iam_oop --tail 200
docker node ps "$(docker node ls -q)"
```

最终交付前必须：

- 修改默认管理员密码；
- 轮换 JWT、HMAC、数据库和 Redis 密钥；
- 关闭 8082 的公网访问；
- 备份 `/srv/iam/mysql` 和 `/srv/iam/redis`；
- 确认 `docker stack services iam` 中所有副本为 Running。

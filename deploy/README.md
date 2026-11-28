# PolyAuth 部署资料

| 文件 | 用途 |
|---|---|
| `Dockerfile.app` | 结构化策略后端镜像（构建上下文 = 仓库根目录） |
| `Dockerfile.oop` | DDD 策略后端镜像（同一 JAR，`APP_BACKEND_STRATEGY=ddd`） |
| `Dockerfile.app-prebuilt` | 预编译 JAR 的离线镜像路径 |
| `Dockerfile.web` | Vue 管理后台 + Nginx 镜像 |
| `iam-stack.yml` | 8 节点完整栈：MySQL、Redis、structured 副本、ddd 副本、Web |
| `iam-stack-backend.yml` | 仅后端子集，便于诊断 |
| `entrypoint.sh` | 把 Docker secret 转为 Spring 环境变量 |
| `nginx-default.conf` | 前端静态资源与 `/api` 反向代理 |
| `COURSE_INTEGRATION.md` | 与课程项目管理系统联调步骤 |

两种策略使用**同一个后端 JAR**，数据库均为 `iam_platform`：

```bash
# 结构化
docker build -f deploy/Dockerfile.app -t <registry>/iam-app:1.0.1 .
# DDD
docker build -f deploy/Dockerfile.oop -t <registry>/iam-oop-app:1.0.1 .
```

栈文件中通过 `APP_BACKEND_STRATEGY=structured|ddd` 选择 Service 实现；启动前按
`COURSE_INTEGRATION.md` 初始化 `backend/src/main/resources/db/` 下的 schema 与 seed。

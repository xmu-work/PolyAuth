# 结构化策略后端镜像（构建上下文 = PolyAuth 仓库根目录）
#   docker build -f deploy/Dockerfile.app -t <registry>/iam-app:1.0.0 .
# 单元测试请在 CI 或构建机上单独跑 mvn test，镜像构建默认跳过测试。

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY backend/pom.xml ./pom.xml
COPY backend/lib-repo ./lib-repo
COPY backend/src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tzdata \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 iam \
 && mkdir -p /app/logs
COPY deploy/entrypoint.sh /app/entrypoint.sh
COPY --from=build /build/target/polyauth-backend-1.0.0-SNAPSHOT.jar /app/app.jar
# sed 保险：源码若在 Windows 编辑过，把 CRLF 换成 LF，避免容器内执行报错
RUN sed -i 's/\r$//' /app/entrypoint.sh \
 && chmod 0755 /app/entrypoint.sh \
 && chown -R iam:iam /app
ENV APP_BACKEND_STRATEGY=structured
ENV LOG_PATH=/app/logs
USER 10001
EXPOSE 8080 8081
ENTRYPOINT ["/app/entrypoint.sh"]

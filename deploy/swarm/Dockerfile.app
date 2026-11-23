# 结构化后端镜像（构建上下文 = PolyAuth 仓库根目录）
#   docker build -f deploy/swarm/Dockerfile.app -t <registry>/iam-app:1.0.0 .
# 单元测试请在 CI 或构建机上单独跑 mvn verify，镜像构建默认跳过测试。

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY structured-backend/pom.xml ./structured-backend/pom.xml
COPY oop-backend/pom.xml ./oop-backend/pom.xml
COPY contract-tests/pom.xml ./contract-tests/pom.xml
COPY structured-backend/src ./structured-backend/src
COPY lib-repo ./lib-repo
RUN mvn -q -pl structured-backend -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tzdata \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 iam \
 && mkdir -p /app/logs
COPY deploy/swarm/entrypoint.sh /app/entrypoint.sh
COPY --from=build /build/structured-backend/target/polyauth-structured-backend-1.0.0-SNAPSHOT.jar /app/app.jar
# sed 保险：源码若在 Windows 编辑过，把 CRLF 换成 LF，避免容器内执行报错
RUN sed -i 's/\r$//' /app/entrypoint.sh \
 && chmod 0755 /app/entrypoint.sh \
 && chown -R iam:iam /app
ENV LOG_PATH=/app/logs
USER 10001
EXPOSE 8080 8081
ENTRYPOINT ["/app/entrypoint.sh"]

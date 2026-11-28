# 后端镜像（构建上下文 = backend 目录）
#   docker build -f backend/Dockerfile -t <registry>/iam-app:1.0.0 backend

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY . .
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 iam \
 && mkdir -p /app/logs
COPY --from=build /build/target/polyauth-backend-1.0.0-SNAPSHOT.jar /app/app.jar
ENV APP_BACKEND_STRATEGY=structured
ENV LOG_PATH=/app/logs
USER 10001
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

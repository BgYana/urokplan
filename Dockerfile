# syntax=docker/dockerfile:1.7

FROM gradle:9.1.0-jdk21-jammy AS builder

USER root
WORKDIR /workspace

COPY . .

RUN --mount=type=cache,id=urokplan-gradle,target=/root/.gradle,sharing=locked \
    --mount=type=cache,id=urokplan-project-gradle,target=/workspace/.gradle,sharing=locked \
    gradle :server:app:buildFatJar --no-daemon --build-cache

FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app

COPY --from=builder --chown=app:app \
    /workspace/server/app/build/libs/urokplan-server.jar \
    /app/app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

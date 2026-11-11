# syntax=docker/dockerfile:1.7

FROM gradle:9.1.0-jdk21-jammy AS builder
USER root
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,id=urokplan-modules-gradle,target=/root/.gradle,sharing=locked \
    --mount=type=cache,id=urokplan-modules-project-gradle,target=/workspace/.gradle,sharing=locked \
    gradle :billing:assemble --no-daemon --build-cache

FROM scratch AS artifact
COPY --from=builder /workspace/billing/build/libs/ /artifacts/

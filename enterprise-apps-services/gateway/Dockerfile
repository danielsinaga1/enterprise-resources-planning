# ========== BUILD STAGE ==========
# Build context is the parent (enterprise-apps-services) so the multi-module
# Gradle build can resolve :gateway and its dependencies.
#   docker build -f gateway/Dockerfile -t irms-gateway .
FROM eclipse-temurin:17-jdk-jammy AS builder
ENV DEBIAN_FRONTEND=noninteractive
WORKDIR /build

# The gateway is a submodule of a multi-module Gradle build; settings.gradle
# includes shared/lims/gateway, so the full project context is required for
# Gradle to evaluate settings. Copy everything, then build only the gateway jar.
COPY . .
RUN ./gradlew :gateway:bootJar --no-daemon -x test

# ========== RUN STAGE ==========
FROM eclipse-temurin:17-jre
ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=Asia/Jakarta

RUN useradd -m -s /bin/bash appuser
WORKDIR /app

COPY --from=builder /build/gateway/build/libs/irms-gateway.jar /app/irms-gateway.jar
RUN chown -R appuser:appuser /app

# Gateway listens on 8080 by default (overridable via GATEWAY_PORT).
ENV GATEWAY_PORT=8080
ENV JAVA_OPTS_APPEND="-Xms256m -Xmx512m -XX:+UseG1GC"

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=10 \
  CMD wget -qO- "http://127.0.0.1:${GATEWAY_PORT}/actuator/health" | grep -q '"status":"UP"' || exit 1

EXPOSE 8080/tcp
USER appuser
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS_APPEND -jar /app/irms-gateway.jar --server.port=${GATEWAY_PORT}"]

# ========== BUILD STAGE ==========
FROM gradle:7.6-jdk17-jammy AS builder
ENV DEBIAN_FRONTEND=noninteractive
WORKDIR /app/neravoxa-service

# Cache dependencies lebih baik
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon || true

# Copy source. Build only the bootable jar (avoids the extra *-plain.jar that a
# full `build` would emit, which would make the COPY *.jar glob ambiguous).
COPY . .
RUN ./gradlew clean bootJar -x test --no-daemon

# ========== RUN STAGE ==========
FROM eclipse-temurin:17-jre
ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=Asia/Jakarta

RUN mkdir -p /dumps /app/keys /app/uploads && \
    useradd -m -s /bin/bash appuser && \
    chown -R appuser:appuser /app /dumps

WORKDIR /app

# Copy jar hasil build
COPY --from=builder /app/neravoxa-service/build/libs/*.jar /app/neravoxa-service.jar
COPY --from=builder /app/neravoxa-service/keys /app/keys

RUN chown -R appuser:appuser /app

ENV PORT=8000
ENV JAVA_OPTS_APPEND="-Xms1g -Xmx1g -XX:+UseG1GC -XX:+UseStringDeduplication -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/dumps/oom.bin -Xlog:gc*:file=/dumps/gc.log:time,level,tags:filecount=5,filesize=100m"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=10 \
  CMD wget -qO- http://127.0.0.1:${PORT}/actuator/health | grep -q '"status":"UP"' || exit 1

EXPOSE 8000/tcp
USER appuser
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS_APPEND -jar /app/neravoxa-service.jar --server.port=${PORT}"]


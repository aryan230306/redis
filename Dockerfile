# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy all source files
COPY src ./src

# Compile everything in one shot (no Maven/Gradle needed)
RUN mkdir -p target/classes && \
    find src/main/java -name "*.java" > sources.txt && \
    javac -d target/classes @sources.txt

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy compiled classes from builder
COPY --from=builder /app/target ./target

# Railway exposes one HTTP port via $PORT.
# Our server reads PORT env var for the HTTP API.
# The TCP Redis port 6379 is also bound internally.
EXPOSE 8080

CMD ["java", "-cp", "target/classes", "com.redisclone.server.RedisServer"]

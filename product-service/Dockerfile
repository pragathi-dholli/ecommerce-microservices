# ── STAGE 1: Build ────────────────────────────────────────────────────────
FROM maven:3.9.5-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy POM first for dependency caching
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build (skip tests — run separately in CI)
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── STAGE 2: Runtime ──────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

# Security: don't run as root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy fat JAR from builder
COPY --from=builder /build/target/product-service-*.jar app.jar

# Ensure appuser owns the app directory
RUN chown -R appuser:appgroup /app

USER appuser

# Expose service port
EXPOSE 8081

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

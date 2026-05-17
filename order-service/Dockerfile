# ── STAGE 1: Build ────────────────────────────────────────────────────────
FROM maven:3.9.5-eclipse-temurin-17 AS builder

WORKDIR /build

# Cache dependencies first
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build (tests run separately in CI)
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── STAGE 2: Runtime ──────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /build/target/order-service-*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8082

ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

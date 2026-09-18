# Build stage
FROM maven:3.9-eclipse-temurin-25-alpine AS builder

WORKDIR /build

# Copy pom.xml first for better layer caching
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source and build
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true

# Production stage
# Java 25 (LTS) JRE — enables virtual threads AND removes synchronized pinning (JEP 491),
# so virtual threads never pin their carrier inside synchronized blocks. Bytecode target is
# 21 (see pom.xml); running 21 bytecode on a 25 runtime is fully supported.
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -g 1001 -S spring && adduser -u 1001 -S spring -G spring

# Copy jar from builder
COPY --from=builder /build/target/*.jar app.jar

# Set ownership
RUN chown -R spring:spring /app

USER spring

# JVM optimization for containers
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 2637

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:2637/api/v1/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

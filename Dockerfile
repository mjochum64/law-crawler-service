# Multi-stage Docker build for Legal Document Crawler
FROM openjdk:17-jdk-slim as builder

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Make Maven wrapper executable
RUN chmod +x ./mvnw

# Download dependencies (cached layer)
RUN ./mvnw dependency:resolve

# Copy source code
COPY src src

# Copy Solr configuration files
COPY solr-config solr-config

# Build application
RUN ./mvnw clean package -DskipTests

# Runtime stage
FROM openjdk:17-jre-slim

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create app user for security
RUN groupadd -r appuser && useradd --no-log-init -r -g appuser appuser

# Set working directory
WORKDIR /app

# Copy built jar from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Copy Solr configuration for runtime access
COPY --from=builder /app/solr-config ./config/solr

# Create directories for data and logs
RUN mkdir -p /app/data /app/logs && chown -R appuser:appuser /app

# Set user
USER appuser

# Environment variables
ENV SPRING_PROFILES_ACTIVE=docker,solr
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:+UseContainerSupport"
ENV CRAWLER_STORAGE_BASE_PATH=/app/data/legal-documents
ENV LOGGING_FILE_PATH=/app/logs

# Expose application port
EXPOSE 8080

# Expose management port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Start application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
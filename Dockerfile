# Multi-stage Docker build for Legal Document Crawler
FROM maven:3.9-eclipse-temurin-17 AS builder

# Set working directory
WORKDIR /app

# Copy pom.xml first for better caching
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src src

# Build application
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:17-jre

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create app user for security
RUN groupadd -r appuser && useradd --no-log-init -r -g appuser appuser

# Set working directory
WORKDIR /app

# Copy built jar from builder stage
COPY --from=builder /app/target/*.jar app.jar

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
# Multi-stage build for Spring Boot layering optimization

# Stage 1: Extract layers
FROM openjdk:17-slim as builder
WORKDIR /builder
COPY target/demo-0.0.1-SNAPSHOT.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 2: Create final image with layers
FROM openjdk:17-slim

# Create app user for security
RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring:spring

WORKDIR /app

# Copy layers in optimal order (dependencies first, app code last)
COPY --from=builder --chown=spring:spring builder/dependencies/ ./
COPY --from=builder --chown=spring:spring builder/spring-boot-loader/ ./
COPY --from=builder --chown=spring:spring builder/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring builder/application/ ./

# Environment variables
ENV JAVA_OPTS=""
ENV SERVER_PORT=8080

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Use Spring Boot's layered approach
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
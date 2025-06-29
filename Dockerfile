# backend/Dockerfile

# استفاده از OpenJDK 17 برای اجرای Spring Boot
FROM openjdk:17-slim

# تنظیم working directory
WORKDIR /app

# کپی JAR فایل از target directory
COPY target/demo-0.0.1-SNAPSHOT.jar app.jar

# تنظیم متغیرهای محیطی
ENV JAVA_OPTS=""
ENV SERVER_PORT=8080

# expose کردن پورت
EXPOSE 8080

# Health check برای Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
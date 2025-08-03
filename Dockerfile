FROM maven:3.8.5-openjdk-17 AS build

WORKDIR /app

COPY pom.xml .


# Copy project files


RUN ./mvnw dependency:go-offline -B

COPY src ./src


# Build the project
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre

WORKDIR /app

# Create directories
RUN mkdir -p /app/data /app/uploads

# Copy jar file
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
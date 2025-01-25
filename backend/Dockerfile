FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src
COPY en-sent.bin ./models/en-sent.bin

RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install dockerize
RUN apk add --no-cache curl && \
    curl -sSL https://github.com/jwilder/dockerize/releases/download/v0.6.1/dockerize-linux-amd64-v0.6.1.tar.gz | tar -xz -C /usr/local/bin

COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/models ./models

EXPOSE 8080

# Wait for PostgreSQL to be available, then start the Spring Boot application
ENTRYPOINT ["dockerize", "-wait", "tcp://postgres:5432", "-timeout", "30s", "java", "-jar", "app.jar"]

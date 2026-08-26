# Stage 1: Build application
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copy pom.xml and resolve dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and build application
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=uat

# Copy compiled jar from build stage
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

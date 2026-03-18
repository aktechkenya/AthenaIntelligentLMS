# syntax=docker/dockerfile:1
# Build context: AthenaIntelligentLMS root
# No athena-lms-common dependency (gateway uses reactive stack, not servlet)
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app

# 1. Resolve dependencies (cached until pom.xml changes)
COPY lms-api-gateway/pom.xml ./pom.xml
RUN mvn dependency:go-offline -q

# 2. Build service
COPY lms-api-gateway/src ./src
RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8105
ENTRYPOINT ["java", "-jar", "app.jar"]

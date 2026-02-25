# syntax=docker/dockerfile:1
# Build context: AthenaIntelligentLMS root (so shared/ is accessible)
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app

# 1. Install shared library
COPY shared/athena-lms-common ./shared/athena-lms-common
RUN --mount=type=cache,target=/root/.m2 \
    cd shared/athena-lms-common && mvn install -q -DskipTests

# 2. Resolve service dependencies (cached until pom.xml changes)
COPY account-service/pom.xml ./pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -q

# 3. Build service
COPY account-service/src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]

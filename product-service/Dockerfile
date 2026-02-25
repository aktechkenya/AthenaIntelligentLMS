# syntax=docker/dockerfile:1
# Build context: AthenaIntelligentLMS root
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app

COPY shared/athena-lms-common ./shared/athena-lms-common
RUN --mount=type=cache,target=/root/.m2 \
    cd shared/athena-lms-common && mvn install -q -DskipTests

COPY product-service/pom.xml ./pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -q

COPY product-service/src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]

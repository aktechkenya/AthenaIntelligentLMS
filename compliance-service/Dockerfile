# syntax=docker/dockerfile:1
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY shared/athena-lms-common ./shared/athena-lms-common
RUN --mount=type=cache,target=/root/.m2 \
    cd shared/athena-lms-common && mvn install -q -DskipTests
COPY compliance-service/pom.xml ./pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -q
COPY compliance-service/src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -q
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8094
ENTRYPOINT ["java", "-jar", "app.jar"]

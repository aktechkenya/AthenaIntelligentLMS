# syntax=docker/dockerfile:1
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY shared/athena-lms-common ./shared/athena-lms-common
COPY payment-service/pom.xml ./pom.xml
COPY payment-service/src ./src
RUN --mount=type=cache,target=/root/.m2 \
    cd shared/athena-lms-common && mvn install -q -DskipTests && \
    cd /app && mvn clean package -DskipTests -q
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]

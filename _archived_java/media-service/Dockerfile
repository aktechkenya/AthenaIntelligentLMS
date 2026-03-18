# syntax=docker/dockerfile:1
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY shared/athena-lms-common ./shared/athena-lms-common
COPY media-service/pom.xml ./pom.xml
COPY media-service/src ./src
RUN --mount=type=cache,target=/root/.m2 \
    cd shared/athena-lms-common && mvn install -q -DskipTests && \
    cd /app && mvn clean package -DskipTests -q
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN mkdir -p /app/storage
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8098
ENTRYPOINT ["java", "-jar", "app.jar"]

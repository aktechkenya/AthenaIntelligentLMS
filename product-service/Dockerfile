# syntax=docker/dockerfile:1
# Build context: AthenaIntelligentLMS root
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app

# 1. Install shared library into local .m2 (filesystem layer)
COPY shared/athena-lms-common ./shared/athena-lms-common
RUN cd shared/athena-lms-common && mvn install -q -DskipTests

# 2. Resolve service dependencies
COPY product-service/pom.xml ./pom.xml
RUN mvn dependency:go-offline -q

# 3. Build service
COPY product-service/src ./src
RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]

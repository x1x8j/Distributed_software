# Multi-stage build: build jar with Maven, run with JRE
FROM maven:3.8.8-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV SERVER_PORT=8081
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar --server.port=${SERVER_PORT}"]

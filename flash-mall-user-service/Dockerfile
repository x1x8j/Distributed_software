# Multi-stage build: build jar with Maven, run with JRE
FROM eclipse-temurin:17-jre
WORKDIR /app

# This Dockerfile expects the module jar to be built on the host and placed at
# flash-mall-user-service/target/*.jar. Build locally with:
#   cd flash-mall-user-service
#   mvn -DskipTests package

ARG JAR_FILE=flash-mall-user-service/target/*.jar
COPY ${JAR_FILE} app.jar

ENV SERVER_PORT=8081
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar --server.port=${SERVER_PORT}"]

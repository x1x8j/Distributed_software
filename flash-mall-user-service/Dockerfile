# Multi-stage build: build jar with Maven, run with JRE
FROM maven:3.8.8-eclipse-temurin-17 AS build
WORKDIR /app
# Copy root parent pom and module sources (build context must be repository root)
COPY pom.xml ./pom.xml
COPY flash-mall-user-service/pom.xml ./flash-mall-user-service/pom.xml
COPY flash-mall-user-service/src ./flash-mall-user-service/src
# Build the specific module using its pom
RUN mvn -f ./flash-mall-user-service/pom.xml -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
# Copy the generated jar from module target
COPY --from=build /app/flash-mall-user-service/target/*.jar app.jar
ENV SERVER_PORT=8081
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar --server.port=${SERVER_PORT}"]

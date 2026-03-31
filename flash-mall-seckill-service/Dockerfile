FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY flash-mall-seckill-service/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar"]

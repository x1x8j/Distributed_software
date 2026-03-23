FROM eclipse-temurin:17-jre
WORKDIR /app

# 在项目根目录执行 mvn -pl flash-mall-product-service -am -DskipTests package 后，
# target/*.jar 会被生成
ARG JAR_FILE=flash-mall-product-service/target/*.jar
COPY ${JAR_FILE} app.jar

ENV SERVER_PORT=8083
EXPOSE 8083
ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar --server.port=${SERVER_PORT}"]

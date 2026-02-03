# Build stage
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV SERVER_PORT=8083
COPY tg-channel-parser/target/*.jar /app/app.jar
EXPOSE 8083
ENTRYPOINT ["java","-jar","/app/app.jar"]


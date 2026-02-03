FROM eclipse-temurin:17-jre
WORKDIR /app
ENV SERVER_PORT=8082
COPY gosuslugi-rss/target/*.jar /app/app.jar
EXPOSE 8082
ENTRYPOINT ["java","-jar","/app/app.jar"]


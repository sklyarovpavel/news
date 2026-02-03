FROM eclipse-temurin:17-jre
WORKDIR /app
ENV SERVER_PORT=8081
COPY fedresurs-crawler/target/*.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]


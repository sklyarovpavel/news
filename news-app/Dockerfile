FROM eclipse-temurin:17-jre
WORKDIR /app
COPY news-app/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]


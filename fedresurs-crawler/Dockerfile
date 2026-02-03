FROM mcr.microsoft.com/playwright/java:v1.42.0-jammy
WORKDIR /app
ENV SERVER_PORT=8081
COPY fedresurs-crawler/target/*.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]


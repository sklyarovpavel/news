package ru.gosuslugi.rss;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@OpenAPIDefinition(
        info = @Info(
                title = "Gosuslugi RSS News API",
                version = "1.0.0",
                description = "REST API для новостей RSS рассылки https://info.gosuslugi.ru/rss/ по диапазону дат."
        )
)
public class GosuslugiRssApplication {
    public static void main(String[] args) {
        SpringApplication.run(GosuslugiRssApplication.class, args);
    }
}


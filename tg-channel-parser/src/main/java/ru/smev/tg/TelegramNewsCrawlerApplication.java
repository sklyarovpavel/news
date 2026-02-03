package ru.smev.tg;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@OpenAPIDefinition(
        info = @Info(
                title = "Telegram News Crawler API",
                version = "1.0.0",
                description = "REST API для поиска постов Telegram-канала https://t.me/s/smev_news по диапазону дат."
        )
)
public class TelegramNewsCrawlerApplication {
    public static void main(String[] args) {
        SpringApplication.run(TelegramNewsCrawlerApplication.class, args);
    }
}


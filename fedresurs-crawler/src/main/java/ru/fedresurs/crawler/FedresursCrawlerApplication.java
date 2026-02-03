package ru.fedresurs.crawler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FedresursCrawlerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FedresursCrawlerApplication.class, args);
    }
}


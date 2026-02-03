package com.example.newsapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NewsAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(NewsAppApplication.class, args);
    }
}


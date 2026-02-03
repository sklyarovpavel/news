package com.example.newsapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Service
public class TelegramSenderService {
    private static final Logger log = LoggerFactory.getLogger(TelegramSenderService.class);

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.chat.id:}")
    private String chatId;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank()
                && chatId != null && !chatId.isBlank();
    }

    public boolean sendText(String text) {
        if (!isConfigured()) {
            log.warn("Telegram credentials are not configured; skip sending");
            return false;
        }
        if (text == null || text.isBlank()) {
            log.info("Telegram: skip sending empty text");
            return true;
        }
        try {
            log.info("Telegram: sending message length={} preview='{}'",
                    text.length(),
                    text.length() > 80 ? text.substring(0, 80) + "…" : text);
            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            String body = "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                    + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Telegram: sent ok status={} bodyLen={}", response.statusCode(),
                        response.body() == null ? 0 : response.body().length());
                return true;
            } else {
                log.warn("Telegram send failed. Status: {}, body: {}", response.statusCode(), response.body());
                return false;
            }
        } catch (IOException | InterruptedException e) {
            log.error("Telegram send error", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }
}


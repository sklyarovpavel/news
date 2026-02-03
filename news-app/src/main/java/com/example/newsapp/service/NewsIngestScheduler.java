package com.example.newsapp.service;

import com.example.newsapp.domain.ResourceItem;
import com.example.newsapp.domain.MessageEntity;
import com.example.newsapp.domain.MessageStatus;
import com.example.newsapp.llm.NewsClassifier;
import com.example.newsapp.repositories.MessageRepository;
import com.example.newsapp.repositories.ResourceItemRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Component
public class NewsIngestScheduler {
    private static final Logger log = LoggerFactory.getLogger(NewsIngestScheduler.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final NewsClassifier classifier;
    private final MessageRepository messageRepository;
    private final ResourceItemRepository resourceItemRepository;

    @Value("${app.news.enabled:true}")
    private boolean enabled;
    @Value("${app.news.limit:200}")
    private int limit;
    @Value("${app.news.windowDays:1}")
    private int windowDays;
    @Value("${app.news.authorUsername:news-bot}")
    private String authorUsername;

    public NewsIngestScheduler(NewsClassifier classifier,
                               MessageRepository messageRepository,
                               ResourceItemRepository resourceItemRepository) {
        this.classifier = classifier;
        this.messageRepository = messageRepository;
        this.resourceItemRepository = resourceItemRepository;
    }

    @Scheduled(fixedDelayString = "${app.news.fixedDelayMs:60000}")
    @Transactional
    public void fetchAndIngest() {
        if (!enabled) {
            return;
        }
        List<ResourceItem> resources = resourceItemRepository.findAll();
        if (resources.isEmpty()) return;

        // Окно выборки едино для всех ресурсов
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String to = now.format(DATE_FMT);
        String from = now.minusDays(Math.max(1, windowDays)).format(DATE_FMT);
        int totalCreated = 0;
        for (ResourceItem r : resources) {
            if (r.getUrl() == null || r.getUrl().isBlank()) continue;
            if (!r.isPollingEnabled()) continue; // используем флаг для управления опросом
            int created = ingestResourceWithPagination(r, from, to);
            totalCreated += created;
        }
        if (totalCreated > 0) {
            log.info("NewsIngest: total created {} message(s) in this cycle", totalCreated);
        }
    }

    private String buildUrlForResource(String resourceUrl, String from, String to, String cursor) {
        StringBuilder sb = new StringBuilder(resourceUrl);
        boolean hasQuery = resourceUrl.contains("?");
        sb.append(hasQuery ? "&" : "?");
        sb.append("from=").append(URLEncoder.encode(from, StandardCharsets.UTF_8));
        sb.append("&to=").append(URLEncoder.encode(to, StandardCharsets.UTF_8));
        sb.append("&limit=").append(limit);
        if (cursor != null && !cursor.isBlank()) {
            sb.append("&cursor=").append(URLEncoder.encode(cursor, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private int ingestResourceWithPagination(ResourceItem r, String from, String to) {
        int createdTotal = 0;
        String cursor = null;
        int pages = 0;
        final int maxPages = 5; // предохранитель
        Integer lastStatus = null;
        String lastError = null;
        while (pages < maxPages) {
            String url = buildUrlForResource(r.getUrl(), from, to, cursor);
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                lastStatus = response.statusCode();
                lastError = null;
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    log.warn("News API error for resource id={} url={} status={} body={}",
                            r.getId(), r.getUrl(), response.statusCode(), truncate(response.body(), 500));
                    break;
                }
                NewsApiResponse payload = objectMapper.readValue(response.body(), NewsApiResponse.class);
                if (payload == null || payload.items == null || payload.items.isEmpty()) {
                    break;
                }
                int created = 0;
                for (NewsItem item : payload.items) {
                    String textForLlm = buildFullText(item);
                    var result = classifier.classify(textForLlm);
                    if (!result.isSuitable()) {
                        continue;
                    }
                    String messageContent = buildMessageContent(item);
                    Optional<MessageEntity> existing = messageRepository.findFirstByContent(messageContent);
                    if (existing.isPresent()) {
                        continue; // уже есть
                    }
                    MessageEntity msg = new MessageEntity();
                    msg.setAuthorUsername(authorUsername);
                    msg.setResource(r);
                    msg.setContent(messageContent);
                    msg.setStatus(MessageStatus.NOT_SENT);
                    messageRepository.save(msg);
                    created++;
                }
                createdTotal += created;
                if (created > 0) {
                    log.info("NewsIngest: resource id={} created {} message(s) on page {}", r.getId(), created, pages + 1);
                }
                cursor = payload.nextCursor;
                if (cursor == null || cursor.isBlank()) {
                    break;
                }
                pages++;
            } catch (IOException | InterruptedException e) {
                log.error("News API fetch error for resource id={} url={}", r.getId(), r.getUrl(), e);
                Thread.currentThread().interrupt();
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                lastStatus = null;
                break;
            }
        }
        // Обновим метрики ресурса
        r.setLastProcessedAt(Instant.now());
        r.setLastPollStatus(lastStatus);
        r.setLastPollError(lastError);
        return createdTotal;
    }

    private static String buildFullText(NewsItem item) {
        String title = item.title != null ? item.title : "";
        String text = item.text != null ? item.text : "";
        String url = item.url != null ? item.url : "";
        return title + "\n\n" + text + "\n\n" + url;
    }

    private static String buildMessageContent(NewsItem item) {
        String title = item.title != null ? item.title : "";
        String url = item.url != null ? item.url : "";
        // Каждая строка будет отправлена отдельно: заголовок, URL
        return title + "\n" + url;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) + "…" : s;
        }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class NewsApiResponse {
        public List<NewsItem> items;
        public int count;
        public String nextCursor;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class NewsItem {
        public String id;
        public String url;
        public String title;
        public String text;
        public String publishedAt;
    }
}


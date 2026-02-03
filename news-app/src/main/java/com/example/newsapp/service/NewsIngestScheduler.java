package com.example.newsapp.service;

import com.example.newsapp.domain.ResourceItem;
import com.example.newsapp.domain.MessageEntity;
import com.example.newsapp.domain.MessageStatus;
import com.example.newsapp.llm.NewsClassifier;
import com.example.newsapp.llm.NewsSummarizer;
import com.example.newsapp.repositories.MessageRepository;
import com.example.newsapp.repositories.ResourceItemRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    private HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final NewsClassifier classifier;
    private final MessageRepository messageRepository;
    private final ResourceItemRepository resourceItemRepository;
    private final NewsSummarizer summarizer;
    private final IngestPersistenceService ingestTx;

    @Value("${app.news.enabled:true}")
    private boolean enabled;
    @Value("${app.news.limit:200}")
    private int limit;
    @Value("${app.news.windowDays:1}")
    private int windowDays;
    @Value("${app.news.authorUsername:news-bot}")
    private String authorUsername;

    @Value("${app.news.requestTimeoutMs:300000}")
    private long requestTimeoutMs;

    public NewsIngestScheduler(NewsClassifier classifier,
                               MessageRepository messageRepository,
                               ResourceItemRepository resourceItemRepository,
                               NewsSummarizer summarizer,
                               IngestPersistenceService ingestTx) {
        this.classifier = classifier;
        this.messageRepository = messageRepository;
        this.resourceItemRepository = resourceItemRepository;
        this.summarizer = summarizer;
        this.ingestTx = ingestTx;
        long ctMs = Math.max(1_000L, requestTimeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(ctMs))
                .build();
    }

    @Scheduled(fixedDelayString = "${app.news.fixedDelayMs:60000}")
    public void fetchAndIngest() {
        if (!enabled) {
            log.info("NewsIngest: disabled, skip cycle");
            return;
        }
        List<ResourceItem> resources = resourceItemRepository.findAll();
        if (resources.isEmpty()) {
            log.info("NewsIngest: no resources found, skip cycle");
            return;
        }

        // Для каждого ресурса используем собственное окно: от lastProcessedAt до текущего момента
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String to = now.format(DATE_FMT);
        log.info("NewsIngest: cycle start resources={} to={} timeoutMs={}",
                resources.size(), to, requestTimeoutMs);
        int totalCreated = 0;
        for (ResourceItem r : resources) {
            if (r.getUrl() == null || r.getUrl().isBlank()) {
                log.info("NewsIngest: skip resource id={} name='{}' reason=empty-url", r.getId(), r.getName());
                continue;
            }
            if (!r.isPollingEnabled()) {
                log.info("NewsIngest: skip resource id={} name='{}' reason=polling-disabled", r.getId(), r.getName());
                continue; // используем флаг для управления опросом
            }
            // from: дата на основе lastProcessedAt ресурса (UTC, формат yyyy-MM-dd)
            Instant lpa = r.getLastProcessedAt();
            OffsetDateTime lpaUtc = (lpa == null ? now.minusDays(Math.max(1, windowDays)) : lpa.atOffset(ZoneOffset.UTC));
            String from = lpaUtc.format(DATE_FMT);
            // гарантируем корректный диапазон
            if (lpaUtc.isAfter(now)) {
                from = to;
            }
            int created = ingestResourceWithPagination(r, from, to);
            totalCreated += created;
        }
        if (totalCreated > 0) {
            log.info("NewsIngest: total created {} message(s) in this cycle", totalCreated);
        } else {
            log.info("NewsIngest: no messages created this cycle");
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
        boolean encounteredError = false;
        while (pages < maxPages) {
            String url = buildUrlForResource(r.getUrl(), from, to, cursor);
            try {
                log.info("NewsIngest: HTTP GET resource id={} name='{}' url='{}' cursor='{}' limit={} timeoutMs={}",
                        r.getId(), r.getName(), r.getUrl(), cursor, limit, requestTimeoutMs);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMillis(Math.max(1_000L, requestTimeoutMs)))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                lastStatus = response.statusCode();
                lastError = null;
                log.info("NewsIngest: HTTP status={} bodyLen={} resourceId={}", response.statusCode(),
                        response.body() == null ? 0 : response.body().length(), r.getId());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    log.warn("News API error for resource id={} url={} status={} body={}",
                            r.getId(), r.getUrl(), response.statusCode(), truncate(response.body(), 500));
                    lastError = "HTTP " + response.statusCode();
                    encounteredError = true;
                    break;
                }
                NewsApiResponse payload = objectMapper.readValue(response.body(), NewsApiResponse.class);
                int itemsCount = (payload == null || payload.items == null) ? 0 : payload.items.size();
                log.info("NewsIngest: parsed items={} nextCursor='{}' resourceId={}", itemsCount,
                        payload == null ? null : payload.nextCursor, r.getId());
                if (itemsCount == 0) {
                    break;
                }
                int created = 0;
                for (NewsItem item : payload.items) {
                    String textForLlm = buildFullText(item);
                    log.info("LLM: classify start resourceId={} titleLen={} textLen={}",
                            r.getId(),
                            item.title == null ? 0 : item.title.length(),
                            item.text == null ? 0 : item.text.length());
                    var result = classifier.classify(textForLlm);
                    log.info("LLM: classify done suitable={} confidence={} resourceId={}",
                            result.isSuitable(), result.getConfidence(), r.getId());
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
                    log.info("LLM: summarize start resourceId={} contentLen={}",
                            r.getId(), textForLlm == null ? 0 : textForLlm.length());
                    String summary = summarizer.summarize(textForLlm);
                    log.info("LLM: summarize done summaryLen={} resourceId={}",
                            summary == null ? 0 : summary.length(), r.getId());
                    msg.setSummary(summary);
                    msg.setStatus(MessageStatus.NOT_SENT);
                    // Сохраняем сообщение отдельной транзакцией
                    ingestTx.saveMessage(msg);
                    created++;
                }
                createdTotal += created;
                if (created > 0) {
                    log.info("NewsIngest: resource id={} created {} message(s) on page {}", r.getId(), created, pages + 1);
                } else {
                    log.info("NewsIngest: resource id={} no suitable messages on page {}", r.getId(), pages + 1);
                }
                cursor = payload.nextCursor;
                if (cursor == null || cursor.isBlank()) {
                    log.info("NewsIngest: no nextCursor, stop pagination resourceId={}", r.getId());
                    break;
                }
                pages++;
            } catch (IOException | InterruptedException e) {
                log.error("News API fetch error for resource id={} url={}", r.getId(), r.getUrl(), e);
                Thread.currentThread().interrupt();
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                lastStatus = null;
                encounteredError = true;
                break;
            }
        }
        // Обновим метрики ресурса отдельной транзакцией
        ingestTx.updateResourceStatus(r.getId(), !encounteredError, lastStatus, lastError);
        log.info("NewsIngest: resource id={} processed, lastStatus={} lastError={}", r.getId(), lastStatus, lastError);
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
        // Заголовок и URL в одном сообщении
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


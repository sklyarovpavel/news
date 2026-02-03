package ru.fedresurs.crawler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crawler")
@Data
public class CrawlerProperties {
    private int rateLimitRps = 2;
    private int requestTimeoutMs = 5000;
    private Retry retry = new Retry();
    private Cache cache = new Cache();
    private Source source = new Source();
    private boolean demoEnabled = true;
    private int maxRangeDays = 31;
    private int defaultLimit = 500;
    private int maxLimit = 1000;

    @Data
    public static class Retry {
        private int maxAttempts = 3;
        private long backoffMs = 500;
    }

    @Data
    public static class Cache {
        private long ttlMs = 10_000;
        private boolean enabled = false;
    }

    @Data
    public static class Source {
        private String baseUrl = "https://fedresurs.ru/news";
        private String userAgent = "FedresursCrawler/1.0 (+https://example.com)";
        private String acceptLanguage = "ru-RU,ru;q=0.9";
    }
}


package ru.smev.tg.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "crawler")
public class CrawlerProperties {
    @NotNull
    @Min(1)
    private Integer rateLimitRps = 2;

    @NotNull
    @Min(100)
    private Long requestTimeoutMs = 5000L;

    @NotNull
    private Retry retry = new Retry();

    @NotNull
    private Cache cache = new Cache();

    @NotNull
    private Source source = new Source();

    @NotNull
    @Min(1)
    @Max(90)
    private Integer maxRangeDays = 31;

    @NotNull
    @Min(1)
    private Integer defaultLimit = 500;

    @NotNull
    @Min(1)
    private Integer maxLimit = 1000;

    @Data
    public static class Retry {
        @NotNull
        @Min(0)
        private Integer maxAttempts = 2;

        @NotNull
        @Min(0)
        private Long backoffMs = 300L;
    }

    @Data
    public static class Cache {
        private boolean enabled = true;
        @NotNull
        @Min(100)
        private Long ttlMs = 10_000L;
    }

    @Data
    public static class Source {
        @NotBlank
        private String baseUrl = "https://t.me/s/smev_news";
        @NotBlank
        private String userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36";
        @NotBlank
        private String acceptLanguage = "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7";
        private String accept = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    }
}


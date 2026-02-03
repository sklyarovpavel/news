package ru.fedresurs.crawler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.fedresurs.crawler.api.dto.NewsItem;
import ru.fedresurs.crawler.api.dto.NewsListResponse;
import ru.fedresurs.crawler.client.FedresursClient;
import ru.fedresurs.crawler.config.CrawlerProperties;
import ru.fedresurs.crawler.util.InMemoryCache;
import ru.fedresurs.crawler.util.RateLimiter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class NewsService {
    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private final FedresursClient client;
    private final CrawlerProperties props;
    private final RateLimiter rateLimiter;
    private final InMemoryCache<String, NewsListResponse> cache;

    public NewsService(FedresursClient client, CrawlerProperties props) {
        this.client = client;
        this.props = props;
        this.rateLimiter = new RateLimiter(Math.max(1, props.getRateLimitRps()));
        this.cache = new InMemoryCache<>(props.getCache().getTtlMs());
    }

    public Mono<NewsListResponse> listNews(LocalDate from, LocalDate to, Integer limit, String cursor) {
        int effectiveLimit = Math.min(
                props.getMaxLimit(),
                limit == null ? props.getDefaultLimit() : Math.max(1, limit)
        );
        String cacheKey = from + "|" + to + "|" + effectiveLimit + "|" + (cursor == null ? "" : cursor);
        if (props.getCache().isEnabled()) {
            var cached = cache.get(cacheKey);
            if (cached.isPresent()) {
                return Mono.just(cached.get());
            }
        }

        Set<String> seenIds = new LinkedHashSet<>();
        AtomicReference<String> lastNextCursor = new AtomicReference<>(null);
        return fetchPages(from, to, effectiveLimit, cursor)
                .doOnNext(page -> lastNextCursor.set(page.nextCursor()))
                .flatMapIterable(FedresursClient.Page::items)
                .filter(Objects::nonNull)
                .filter(item -> withinRange(item.publishedAtIso(), from, to))
                .filter(item -> {
                    boolean isNew = !seenIds.contains(item.id());
                    if (isNew) seenIds.add(item.id());
                    return isNew;
                })
                .map(item -> NewsItem.builder()
                        .id(item.id())
                        .url(item.url())
                        .title(item.title())
                        .publishedAt(item.publishedAtIso())
                        .build())
                .take(effectiveLimit)
                .flatMap(item ->
                        rateLimiter.limit(client.fetchContent(item.getUrl())
                                        .timeout(java.time.Duration.ofMillis(Math.max(props.getRequestTimeoutMs(), 8000)))
                                        .onErrorReturn("")
                                )
                                .map(content -> {
                                    item.setText(content);
                                    return item;
                                })
                )
                .collectList()
                .map(items -> {
                    String next = lastNextCursor.get();
                    NewsListResponse resp = NewsListResponse.builder()
                            .items(items)
                            .count(items.size())
                            .nextCursor(next)
                            .build();
                    if (props.getCache().isEnabled()) {
                        cache.put(cacheKey, resp);
                    }
                    log.info("Fetched {} items, nextCursor={}", resp.getCount(), resp.getNextCursor());
                    return resp;
                });
    }

    private Flux<FedresursClient.Page> fetchPages(LocalDate from, LocalDate to, int limit, String initialCursor) {
        Mono<FedresursClient.Page> first = rateLimiter.limit(client.fetchPage(initialCursor, from, to, limit));
        return first.expand(page -> {
            if (page.nextCursor() == null) {
                return Mono.empty();
            }
            return rateLimiter.limit(client.fetchPage(page.nextCursor(), from, to, limit));
        }).filter(p -> p.items() != null && !p.items().isEmpty());
    }

    private record SimpleItem(String id, String url, String text, String publishedAt) {}

    private boolean withinRange(String publishedAtIso, LocalDate from, LocalDate to) {
        try {
            LocalDate d = OffsetDateTime.parse(publishedAtIso).toLocalDate();
            return (d.isEqual(from) || d.isAfter(from)) && (d.isEqual(to) || d.isBefore(to));
        } catch (Exception e) {
            return true; // если не смогли распарсить — не отбрасываем
        }
    }
}


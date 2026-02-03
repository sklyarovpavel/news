package ru.smev.tg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.smev.tg.api.model.NewsItem;
import ru.smev.tg.api.model.NewsListResponse;
import ru.smev.tg.config.CrawlerProperties;
import ru.smev.tg.telegram.RawPost;
import ru.smev.tg.telegram.RenderedTelegramClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class NewsService {
    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private final RenderedTelegramClient client;
    private final CrawlerProperties props;
    private final SimpleResponseCache cache;

    public NewsService(RenderedTelegramClient client, CrawlerProperties props) {
        this.client = client;
        this.props = props;
        this.cache = new SimpleResponseCache(props.getCache().isEnabled(), props.getCache().getTtlMs());
    }

    public Mono<NewsListResponse> listNews(LocalDate from, LocalDate to, Integer limit, String cursor) {
        int effectiveLimit = limit == null ? props.getDefaultLimit() : Math.min(limit, props.getMaxLimit());
        int offset = parseCursor(cursor);

        String cacheKey = cacheKey(from, to, effectiveLimit, offset);
        log.info("NewsService: request from={} to={} limit={} offset={}", from, to, effectiveLimit, offset);
        NewsListResponse cached = cache.get(cacheKey);
        if (cached != null) {
            log.info("NewsService: cache hit for key={}", cacheKey);
            return Mono.just(cached);
        }

        int target = offset + effectiveLimit + 1;
        log.info("NewsService: fetching posts target={}", target);
        return client.fetchPosts(0, target)
                .publishOn(Schedulers.boundedElastic())
                .map(raw -> {
                    log.info("NewsService: raw posts fetched = {}", raw.size());
                    return toFiltered(raw, from, to);
                })
                .map(filtered -> toResponse(filtered, offset, effectiveLimit))
                .doOnNext(resp -> cache.put(cacheKey, resp));
    }

    private List<RawPost> toFiltered(List<RawPost> raw, LocalDate from, LocalDate to) {
        OffsetDateTime start = from.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime end = to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime().minusNanos(1);

        // сортировка по дате публикации (новые сверху), удаление дубликатов id
        List<RawPost> sorted = new ArrayList<>(raw);
        sorted.sort(Comparator.comparing(RawPost::getPublishedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        Set<String> seen = ConcurrentHashMap.newKeySet();
        return sorted.stream()
                .filter(p -> p.getPublishedAt() != null)
                .filter(p -> !p.getPublishedAt().isBefore(start) && !p.getPublishedAt().isAfter(end))
                .filter(p -> seen.add(p.getId()))
                .collect(Collectors.toList());
    }

    private NewsListResponse toResponse(List<RawPost> filtered, int offset, int limit) {
        int fromIdx = Math.min(offset, filtered.size());
        int toIdx = Math.min(fromIdx + limit, filtered.size());
        List<RawPost> page = filtered.subList(fromIdx, toIdx);
        List<NewsItem> items = page.stream().map(this::toItem).filter(Objects::nonNull).toList();
        String next = (filtered.size() > toIdx) ? "o:" + toIdx : null;
        return NewsListResponse.builder()
                .items(items)
                .count(items.size())
                .nextCursor(next)
                .build();
    }

    private NewsItem toItem(RawPost p) {
        if (p == null) return null;
        String title = deriveTitle(p.getText());
        return NewsItem.builder()
                .id(p.getId())
                .url(p.getUrl())
                .title(title)
                .text(p.getText())
                .publishedAt(p.getPublishedAt())
                .build();
    }

    private String deriveTitle(String text) {
        if (text == null || text.isBlank()) return "";
        String[] lines = text.split("\\R+");
        for (String line : lines) {
            if (line != null) {
                String trimmed = line.trim();
                if (!trimmed.isBlank()) {
                    return trimmed.length() > 160 ? trimmed.substring(0, 160) : trimmed;
                }
            }
        }
        String t = text.trim();
        return t.length() > 160 ? t.substring(0, 160) : t;
    }

    private int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        if (cursor.startsWith("o:")) {
            try {
                return Integer.parseInt(cursor.substring(2));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private String cacheKey(LocalDate from, LocalDate to, int limit, int offset) {
        return from + "|" + to + "|" + limit + "|" + offset;
    }

    static final class SimpleResponseCache {
        private final boolean enabled;
        private final long ttlMs;
        private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();
        private final AtomicLong nowProvider = new AtomicLong(System.currentTimeMillis());

        SimpleResponseCache(boolean enabled, long ttlMs) {
            this.enabled = enabled;
            this.ttlMs = ttlMs;
        }

        NewsListResponse get(String key) {
            if (!enabled) return null;
            Entry e = map.get(key);
            if (e == null) return null;
            if (System.currentTimeMillis() - e.createdAt > ttlMs) {
                map.remove(key);
                return null;
            }
            return e.value;
        }

        void put(String key, NewsListResponse value) {
            if (!enabled) return;
            map.put(key, new Entry(value));
        }

        record Entry(NewsListResponse value, long createdAt) {
            Entry(NewsListResponse value) {
                this(value, System.currentTimeMillis());
            }
        }
    }
}


package ru.gosuslugi.rss.service;

import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.gosuslugi.rss.api.model.NewsItem;
import ru.gosuslugi.rss.api.model.NewsListResponse;
import ru.gosuslugi.rss.config.CrawlerProperties;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class NewsService {
    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private final WebClient webClient;
    private final CrawlerProperties props;

    public NewsService(CrawlerProperties props) {
        this.props = props;
        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, props.getSource().getUserAgent())
                .defaultHeader(HttpHeaders.ACCEPT, props.getSource().getAccept())
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, props.getSource().getAcceptLanguage())
                .build();
    }

    public Mono<NewsListResponse> listNews(LocalDate from, LocalDate to, int limit, String cursor) {
        int offset = parseCursor(cursor);
        return fetchRss()
                .publishOn(Schedulers.boundedElastic())
                .map(this::parseItems)
                .map(items -> filterAndSort(items, from, to))
                .map(list -> toResponse(list, offset, limit));
    }

    private Mono<String> fetchRss() {
        String url = props.getSource().getRssUrl();
        log.info("Fetching RSS: {}", url);
        return webClient.get()
                .uri(url)
                .accept(MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.ALL)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(java.time.Duration.ofMillis(props.getRequestTimeoutMs()));
    }

    private List<NewsItem> parseItems(String xml) {
        List<NewsItem> items = new ArrayList<>();
        try {
            var dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            var db = dbf.newDocumentBuilder();
            var doc = db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            var nodeList = doc.getElementsByTagName("item");
            for (int i = 0; i < nodeList.getLength(); i++) {
                var node = nodeList.item(i);
                var children = node.getChildNodes();
                String id = null, link = null, title = null, description = null, pub = null, guid = null;
                for (int j = 0; j < children.getLength(); j++) {
                    var ch = children.item(j);
                    if (ch.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;
                    String name = ch.getNodeName();
                    String text = ch.getTextContent();
                    switch (name) {
                        case "guid" -> guid = safe(text);
                        case "link" -> link = safe(text);
                        case "title" -> title = safe(text);
                        case "description" -> description = safe(text);
                        case "pubDate" -> pub = safe(text);
                    }
                }
                id = guid != null && !guid.isBlank() ? guid : (link != null ? "rss-" + Integer.toHexString(link.hashCode()) : null);
                if (id == null || link == null || title == null) continue;
                OffsetDateTime publishedAt = parsePublishedAt(pub);
                String text = description == null ? "" : Jsoup.parse(description).text();
                items.add(NewsItem.builder()
                        .id(id)
                        .url(link)
                        .title(title)
                        .text(text)
                        .publishedAt(publishedAt)
                        .build());
            }
        } catch (Exception e) {
            log.warn("Failed to parse RSS: {}", e.toString(), e);
        }
        return items;
    }

    private List<NewsItem> filterAndSort(List<NewsItem> items, LocalDate from, LocalDate to) {
        OffsetDateTime start = from.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime end = to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime().minusNanos(1);
        Set<String> seen = new LinkedHashSet<>();
        return items.stream()
                .filter(Objects::nonNull)
                .filter(it -> it.getPublishedAt() != null)
                .filter(it -> !it.getPublishedAt().isBefore(start) && !it.getPublishedAt().isAfter(end))
                .filter(it -> seen.add(it.getId()))
                .sorted(Comparator.comparing(NewsItem::getPublishedAt).reversed())
                .toList();
    }

    private NewsListResponse toResponse(List<NewsItem> filtered, int offset, int limit) {
        int fromIdx = Math.min(offset, filtered.size());
        int toIdx = Math.min(fromIdx + limit, filtered.size());
        List<NewsItem> page = filtered.subList(fromIdx, toIdx);
        String next = (filtered.size() > toIdx) ? "o:" + toIdx : null;
        return NewsListResponse.builder()
                .items(page)
                .count(page.size())
                .nextCursor(next)
                .build();
    }

    private int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        if (cursor.startsWith("o:")) {
            try {
                return Integer.parseInt(cursor.substring(2));
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private OffsetDateTime parsePublishedAt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        raw = raw.trim();
        // Примеры в RSS:
        // - lastBuildDate: Tue, 03 Feb 2026 05:17:10 +0300 (RFC1123)
        // - item pubDate: "02.02.2026 13:49:46" (локальная дата/время, без зоны)
        try {
            // Попытка RFC_1123
            var rfc = DateTimeFormatter.RFC_1123_DATE_TIME;
            return OffsetDateTime.parse(raw, rfc);
        } catch (Exception ignored) {
        }
        try {
            var fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
            var ldt = java.time.LocalDateTime.parse(raw, fmt);
            ZoneId zone = ZoneId.of(props.getSource().getDefaultZoneId());
            return ldt.atZone(zone).toOffsetDateTime();
        } catch (Exception ignored) {
        }
        // fallback: now
        return OffsetDateTime.now();
    }

    private static String safe(String s) {
        return s == null ? null : s.trim();
    }
}


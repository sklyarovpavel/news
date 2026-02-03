package ru.fedresurs.crawler.client;

import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

public interface FedresursClient {
    record Page(List<Item> items, String nextCursor) {}
    record Item(String id, String url, String title, String publishedAtIso) {}

    Mono<Page> fetchPage(String cursor, LocalDate from, LocalDate to, int limit);

    /**
     * Загрузить и распарсить полный текст новости по URL.
     */
    Mono<String> fetchContent(String url);
}


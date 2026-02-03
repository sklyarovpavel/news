package ru.fedresurs.crawler.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.fedresurs.crawler.client.FedresursClient;
import ru.fedresurs.crawler.config.CrawlerProperties;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class NewsServiceTest {
    FedresursClient client;
    CrawlerProperties props;
    NewsService service;

    @BeforeEach
    void setup() {
        client = Mockito.mock(FedresursClient.class);
        props = new CrawlerProperties();
        service = new NewsService(client, props);
    }

    @Test
    void paginatesAndDeduplicates() {
        FedresursClient.Page page1 = new FedresursClient.Page(
                List.of(
                        new FedresursClient.Item("a", "u1", "t1", "2026-01-01T10:00:00+03:00"),
                        new FedresursClient.Item("b", "u2", "t2", "2026-01-01T11:00:00+03:00")
                ),
                "next-1"
        );
        FedresursClient.Page page2 = new FedresursClient.Page(
                List.of(
                        new FedresursClient.Item("b", "u2", "t2", "2026-01-01T11:00:00+03:00"),
                        new FedresursClient.Item("c", "u3", "t3", "2026-01-01T12:00:00+03:00")
                ),
                null
        );
        when(client.fetchPage(isNull(), any(), any(), anyInt())).thenReturn(Mono.just(page1));
        when(client.fetchPage(eq("next-1"), any(), any(), anyInt())).thenReturn(Mono.just(page2));
        when(client.fetchContent(anyString())).thenReturn(Mono.just("content"));

        StepVerifier.create(service.listNews(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-10"), 10, null))
                .expectNextMatches(resp -> resp.getCount() == 3 && resp.getItems().stream().map(i -> i.getId()).toList().containsAll(List.of("a","b","c")))
                .verifyComplete();
    }
}


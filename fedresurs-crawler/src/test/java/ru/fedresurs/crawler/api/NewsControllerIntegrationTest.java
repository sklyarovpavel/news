package ru.fedresurs.crawler.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.fedresurs.crawler.api.dto.NewsListResponse;
import ru.fedresurs.crawler.client.FedresursClient;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NewsControllerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    FedresursClient fedresursClient;

    @Test
    void getNewsSuccessWithMockClient() {
        var page = new FedresursClient.Page(
                List.of(
                        new FedresursClient.Item("news-1", "https://fedresurs.ru/news/1", "Тест", "2026-01-05T10:00:00+03:00"),
                        new FedresursClient.Item("news-2", "https://fedresurs.ru/news/2", "Тест 2", "2026-01-06T10:00:00+03:00")
                ),
                null
        );
        when(fedresursClient.fetchPage(isNull(), any(LocalDate.class), any(LocalDate.class), anyInt()))
                .thenReturn(Mono.just(page));
        when(fedresursClient.fetchContent(anyString())).thenReturn(Mono.just("content"));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/news")
                        .queryParam("from", "2026-01-01")
                        .queryParam("to", "2026-01-10")
                        .queryParam("limit", "5")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(NewsListResponse.class)
                .value(resp -> {
                    assert resp.getItems() != null;
                    assert resp.getCount() == resp.getItems().size();
                    assert resp.getCount() == 2;
                });
    }
}


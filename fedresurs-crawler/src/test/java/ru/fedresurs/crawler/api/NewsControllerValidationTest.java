package ru.fedresurs.crawler.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.fedresurs.crawler.api.dto.ProblemDetails;
import ru.fedresurs.crawler.config.CrawlerProperties;
import ru.fedresurs.crawler.service.NewsService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = NewsController.class)
@Import({GlobalExceptionHandler.class, CrawlerProperties.class})
class NewsControllerValidationTest {

    @Autowired
    WebTestClient webClient;

    @MockBean
    NewsService newsService;

    @Test
    void invalidRangeFromAfterTo() {
        when(newsService.listNews(any(), any(), any(), any())).thenReturn(Mono.empty());
        webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/news")
                        .queryParam("from", "2026-02-01")
                        .queryParam("to", "2026-01-01")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ProblemDetails.class)
                .value(body -> {
                    assert body.getStatus() == 400;
                });
    }

    @Test
    void invalidRangeTooWide() {
        when(newsService.listNews(any(), any(), any(), any())).thenReturn(Mono.empty());
        webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/news")
                        .queryParam("from", "2026-01-01")
                        .queryParam("to", "2026-03-15")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ProblemDetails.class)
                .value(body -> {
                    assert body.getStatus() == 400;
                });
    }
}


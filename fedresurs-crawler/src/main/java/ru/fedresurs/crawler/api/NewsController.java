package ru.fedresurs.crawler.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.fedresurs.crawler.api.dto.NewsListResponse;
import ru.fedresurs.crawler.config.CrawlerProperties;
import ru.fedresurs.crawler.exception.BadRequestException;
import ru.fedresurs.crawler.service.NewsService;

import java.time.Duration;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
@Validated
public class NewsController {
    private static final Logger log = LoggerFactory.getLogger(NewsController.class);
    private final NewsService service;
    private final CrawlerProperties props;

    public NewsController(NewsService service, CrawlerProperties props) {
        this.service = service;
        this.props = props;
    }

    @GetMapping("/news")
    public Mono<ResponseEntity<NewsListResponse>> listNews(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "limit", required = false) @Min(1) @Max(1000) Integer limit,
            @RequestParam(value = "cursor", required = false) String cursor
    ) {
        validateRange(from, to);
        return service.listNews(from, to, limit, cursor)
                .map(ResponseEntity::ok)
                .timeout(Duration.ofMillis(Math.max(15_000, props.getRequestTimeoutMs() * 4L)));
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BadRequestException("'from' должен быть <= 'to' и диапазон не должен превышать 31 день");
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        if (days > props.getMaxRangeDays()) {
            throw new BadRequestException("'from' должен быть <= 'to' и диапазон не должен превышать 31 день");
        }
    }
}


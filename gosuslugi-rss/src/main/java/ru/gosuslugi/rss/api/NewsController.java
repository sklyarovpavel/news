package ru.gosuslugi.rss.api;

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
import ru.gosuslugi.rss.api.model.NewsListResponse;
import ru.gosuslugi.rss.config.CrawlerProperties;
import ru.gosuslugi.rss.service.NewsService;
import ru.gosuslugi.rss.web.BadRequestException;

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
        log.info("HTTP GET /news from={} to={} limit={} cursor={}", from, to, limit, cursor);
        validateRange(from, to);
        int effectiveLimit = limit == null ? props.getDefaultLimit() : Math.min(props.getMaxLimit(), Math.max(1, limit));
        return service.listNews(from, to, effectiveLimit, cursor).map(ResponseEntity::ok);
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw BadRequestException.of("Неверный формат параметра запроса: from/to");
        }
        if (from.isAfter(to)) {
            throw BadRequestException.of("'from' должен быть <= 'to' и диапазон не должен превышать %d день".formatted(props.getMaxRangeDays()));
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        if (days > props.getMaxRangeDays()) {
            throw BadRequestException.of("Слишком широкий диапазон: не более %d дней".formatted(props.getMaxRangeDays()));
        }
    }
}


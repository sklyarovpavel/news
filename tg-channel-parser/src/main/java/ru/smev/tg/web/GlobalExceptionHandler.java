package ru.smev.tg.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.smev.tg.api.model.Problem;

import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Problem> handleBadRequest(BadRequestException ex) {
        String id = UUID.randomUUID().toString();
        Problem body = Problem.builder()
                .type("https://httpstatuses.com/400")
                .title("Bad Request")
                .status(400)
                .detail(ex.getMessage())
                .instance(id)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("X-Request-Id", id)
                .body(body);
    }

    @ExceptionHandler(TooWideRangeException.class)
    public ResponseEntity<Problem> handleTooWide(TooWideRangeException ex) {
        String id = UUID.randomUUID().toString();
        Problem body = Problem.builder()
                .type("https://httpstatuses.com/429")
                .title("Too Many Requests")
                .status(429)
                .detail(ex.getMessage())
                .instance(id)
                .build();
        return ResponseEntity.status(429)
                .header("X-Request-Id", id)
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Problem> handleAny(Exception ex) {
        String id = UUID.randomUUID().toString();
        log.error("Unhandled error [{}]: {}", id, ex.toString(), ex);
        Problem body = Problem.builder()
                .type("https://httpstatuses.com/500")
                .title("Internal Server Error")
                .status(500)
                .detail("Внутренняя ошибка")
                .instance(id)
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("X-Request-Id", id)
                .body(body);
    }
}


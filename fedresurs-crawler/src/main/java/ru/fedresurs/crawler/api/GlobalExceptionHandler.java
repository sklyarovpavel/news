package ru.fedresurs.crawler.api;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;
import ru.fedresurs.crawler.api.dto.ProblemDetails;
import ru.fedresurs.crawler.exception.BadGatewayException;
import ru.fedresurs.crawler.exception.BadRequestException;
import ru.fedresurs.crawler.exception.TooManyRequestsException;

import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ProblemDetails> handleBadRequest(BadRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ProblemDetails> handleTooMany(TooManyRequestsException ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler(BadGatewayException.class)
    public ResponseEntity<ProblemDetails> handleBadGateway(BadGatewayException ex) {
        return problem(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ProblemDetails> handleValidation(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ProblemDetails> handleTypeMismatch(ServerWebInputException ex) {
        // Часто возникает при неверном формате даты в query-параметрах
        return problem(HttpStatus.BAD_REQUEST, "Неверный формат параметра запроса: " + ex.getReason());
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ProblemDetails> handleAny(Throwable ex) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");
    }

    private ResponseEntity<ProblemDetails> problem(HttpStatus status, String detail) {
        ProblemDetails body = ProblemDetails.builder()
                .type("https://httpstatuses.com/" + status.value())
                .title(status.getReasonPhrase())
                .status(status.value())
                .detail(detail)
                .instance(UUID.randomUUID().toString())
                .build();
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}


package ru.smev.tg.rate;

import reactor.core.publisher.Mono;

public interface RateLimiter {
    Mono<Void> acquire();
}


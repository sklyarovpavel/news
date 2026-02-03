package ru.fedresurs.crawler.util;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Простой лимитер "rps" на основе задержек между вызовами.
 */
public class RateLimiter {
    private final long minIntervalMillis;
    private final AtomicLong lastTime = new AtomicLong(0L);
    private final Semaphore single = new Semaphore(1);

    public RateLimiter(int requestsPerSecond) {
        if (requestsPerSecond <= 0) {
            throw new IllegalArgumentException("requestsPerSecond must be > 0");
        }
        this.minIntervalMillis = 1000L / requestsPerSecond;
    }

    public <T> Mono<T> limit(Mono<T> mono) {
        return Mono.defer(() -> {
            long now = System.currentTimeMillis();
            long prev = lastTime.getAndSet(now);
            long elapsed = now - prev;
            long wait = Math.max(0, minIntervalMillis - elapsed);
            return Mono.delay(Duration.ofMillis(wait)).then(mono);
        });
    }
}


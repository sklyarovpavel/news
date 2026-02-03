package ru.smev.tg.rate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.smev.tg.config.CrawlerProperties;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SimpleRateLimiter implements RateLimiter {
    private static final Logger log = LoggerFactory.getLogger(SimpleRateLimiter.class);

    private final long minIntervalMs;
    private final AtomicLong lastAcquireAt = new AtomicLong(0);

    public SimpleRateLimiter(CrawlerProperties properties) {
        int rps = Math.max(1, properties.getRateLimitRps());
        this.minIntervalMs = Math.max(0, Math.round(1000.0 / rps));
        log.info("RateLimiter configured with minInterval={} ms (rps={})", minIntervalMs, rps);
    }

    @Override
    public Mono<Void> acquire() {
        return Mono.fromRunnable(() -> {})
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.defer(() -> {
                    long now = System.currentTimeMillis();
                    long last = lastAcquireAt.get();
                    long wait = Math.max(0, (last + minIntervalMs) - now);
                    long target = now + wait;
                    if (wait == 0) {
                        lastAcquireAt.set(now);
                        return Mono.empty();
                    }
                    return Mono.delay(Duration.ofMillis(wait), Schedulers.boundedElastic())
                            .doOnSuccess(ignored -> lastAcquireAt.set(target))
                            .then();
                }));
    }
}


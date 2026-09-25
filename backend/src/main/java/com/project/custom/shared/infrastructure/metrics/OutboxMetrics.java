package com.project.custom.shared.infrastructure.metrics;

import com.project.custom.shared.infrastructure.outbox.OutboxEventJpaRepository;
import com.project.custom.shared.infrastructure.outbox.PendingOutbox;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Outbox gauges (research R-30, FR-029): the number of unsent events and the age of the oldest one. The query
 * result is cached for {@link #CACHE_TTL}, so that a Prometheus scrape does not always hit the database.
 */
@Component
class OutboxMetrics {

    static final String PENDING = "shop.outbox.pending";
    static final String OLDEST = "shop.outbox.oldest";
    static final Duration CACHE_TTL = Duration.ofSeconds(15);

    private final OutboxEventJpaRepository repository;
    private final Clock clock;

    private PendingOutbox cached;
    private Instant cachedAt;

    OutboxMetrics(OutboxEventJpaRepository repository, MeterRegistry registry, Clock clock) {
        this.repository = repository;
        this.clock = clock;
        Gauge.builder(PENDING, this, OutboxMetrics::pending)
                .description("Outbox events not sent yet")
                .strongReference(true)
                .register(registry);
        Gauge.builder(OLDEST, this, OutboxMetrics::oldestAgeSeconds)
                .description("Age of the oldest unsent outbox event; 0 when none")
                .baseUnit("seconds")
                .strongReference(true)
                .register(registry);
    }

    double pending() {
        return snapshot().pending();
    }

    double oldestAgeSeconds() {
        return snapshot().oldest()
                .map(oldest -> Math.max(0, Duration.between(oldest.toInstant(), clock.instant()).toMillis() / 1000.0))
                .orElse(0.0);
    }

    private synchronized PendingOutbox snapshot() {
        Instant now = clock.instant();
        if (cached == null || !now.isBefore(cachedAt.plus(CACHE_TTL))) {
            cached = repository.findPending();
            cachedAt = now;
        }
        return cached;
    }
}

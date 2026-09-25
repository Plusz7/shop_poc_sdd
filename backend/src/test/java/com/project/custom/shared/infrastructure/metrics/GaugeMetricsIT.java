package com.project.custom.shared.infrastructure.metrics;

import com.project.custom.shared.infrastructure.outbox.OutboxEventJpaRepository;
import com.project.custom.support.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Outbox and migration gauges (research R-26, R-30; FR-029, FR-030) against a real SQL Server.
 */
class GaugeMetricsIT extends IntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

    @Autowired
    private OutboxEventJpaRepository outboxRepository;

    @Autowired
    private Flyway flyway;

    @Autowired
    private MeterRegistry applicationRegistry;

    private final MutableClock clock = new MutableClock(NOW);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void createGauges() {
        new OutboxMetrics(outboxRepository, registry, clock);
    }

    @Test
    void emptyOutboxReadsZero() {
        assertThat(pending()).isZero();
        assertThat(oldest()).isZero();
    }

    @Test
    void unsentEventsAreCountedWithTheAgeOfTheOldest() {
        insertOutboxEvent(NOW.minusSeconds(400), null);
        insertOutboxEvent(NOW.minusSeconds(10), null);
        insertOutboxEvent(NOW.minusSeconds(900), NOW.minusSeconds(800));

        assertThat(pending()).isEqualTo(2);
        assertThat(oldest()).isCloseTo(400, within(1.0));
    }

    @Test
    void resultIsCachedForFifteenSeconds() {
        insertOutboxEvent(NOW.minusSeconds(60), null);
        assertThat(pending()).isEqualTo(1);

        insertOutboxEvent(NOW.minusSeconds(30), null);
        clock.advance(Duration.ofSeconds(14));
        assertThat(pending()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(1));
        assertThat(pending()).isEqualTo(2);
    }

    @Test
    void applicationGaugesAreRegistered() {
        assertThat(applicationRegistry.find("shop.outbox.pending").gauge()).isNotNull();
        assertThat(applicationRegistry.find("shop.outbox.oldest").gauge()).isNotNull();
        assertThat(applicationRegistry.find("shop.outbox.oldest").gauge().getId().getBaseUnit()).isEqualTo("seconds");
    }

    @Test
    void migrationsAreCountedByStateAfterStartup() {
        int applied = flyway.info().applied().length;

        assertThat(applied).isGreaterThanOrEqualTo(5);
        assertThat(metrics().value("shop.flyway.migrations", "state", "success")).isEqualTo(applied);
        assertThat(metrics().value("shop.flyway.migrations", "state", "failed")).isZero();
        assertThat(metrics().value("shop.flyway.migrations", "state", "pending")).isZero();
    }

    private double pending() {
        return registry.get("shop.outbox.pending").gauge().value();
    }

    private double oldest() {
        return registry.get("shop.outbox.oldest").gauge().value();
    }

    private void insertOutboxEvent(Instant createdAt, Instant sentAt) {
        jdbcTemplate.update("""
                        INSERT INTO outbox_event (id, type, aggregate_id, payload, created_at, sent_at, attempts)
                        VALUES (?, 'OrderPaid', 'ORD-TEST', '{}', ?, ?, 0)""",
                UUID.randomUUID(), createdAt.atOffset(ZoneOffset.UTC),
                sentAt == null ? null : sentAt.atOffset(ZoneOffset.UTC));
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

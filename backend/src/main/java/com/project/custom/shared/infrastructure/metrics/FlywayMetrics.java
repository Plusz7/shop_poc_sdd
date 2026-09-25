package com.project.custom.shared.infrastructure.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Database migrations by state (research R-26, FR-030): Boot does not publish Flyway metrics, so they are
 * counted once, after startup.
 */
@Component
class FlywayMetrics {

    static final String MIGRATIONS = "shop.flyway.migrations";

    enum State { SUCCESS, FAILED, PENDING }

    private final Flyway flyway;
    private final Map<State, AtomicLong> counts = new EnumMap<>(State.class);

    FlywayMetrics(Flyway flyway, MeterRegistry registry) {
        this.flyway = flyway;
        for (State state : State.values()) {
            AtomicLong count = new AtomicLong();
            counts.put(state, count);
            Gauge.builder(MIGRATIONS, count, AtomicLong::get)
                    .description("Database migrations by state")
                    .tag("state", state.name().toLowerCase(Locale.ROOT))
                    .register(registry);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void countMigrations() {
        MigrationInfo[] all = flyway.info().all();
        counts.get(State.SUCCESS).set(Arrays.stream(all)
                .filter(info -> info.getState().isApplied() && !info.getState().isFailed()).count());
        counts.get(State.FAILED).set(Arrays.stream(all).filter(info -> info.getState().isFailed()).count());
        counts.get(State.PENDING).set(flyway.info().pending().length);
    }
}

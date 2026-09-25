package com.project.custom.payment.infrastructure.stripe;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Metrics of Stripe calls (contracts/metrics.md §2): the duration and outcome of every attempt and every
 * retry. Recorded immediately — the calls are always made outside a database transaction. Every label
 * combination is registered with {@code 0} at startup.
 */
class StripeCallMetrics {

    static final String CALLS = "shop.stripe.calls";
    static final String RETRIES = "shop.stripe.retries";

    private final Map<StripeOperation, Map<CallOutcome, Timer>> calls = new EnumMap<>(StripeOperation.class);
    private final Map<StripeOperation, Counter> retries = new EnumMap<>(StripeOperation.class);

    StripeCallMetrics(MeterRegistry registry) {
        for (StripeOperation operation : StripeOperation.values()) {
            Map<CallOutcome, Timer> byOutcome = new EnumMap<>(CallOutcome.class);
            for (CallOutcome outcome : CallOutcome.values()) {
                byOutcome.put(outcome, Timer.builder(CALLS)
                        .description("Stripe call attempts")
                        .tag("operation", label(operation))
                        .tag("outcome", label(outcome))
                        .serviceLevelObjectives(Duration.ofMillis(100), Duration.ofMillis(300), Duration.ofSeconds(1),
                                Duration.ofSeconds(3), Duration.ofSeconds(10))
                        .register(registry));
            }
            calls.put(operation, byOutcome);
            retries.put(operation, Counter.builder(RETRIES)
                    .description("Retries of Stripe calls")
                    .tag("operation", label(operation))
                    .register(registry));
        }
    }

    void attempt(StripeOperation operation, CallOutcome outcome, Duration duration) {
        calls.get(operation).get(outcome).record(duration);
    }

    void retry(StripeOperation operation) {
        retries.get(operation).increment();
    }

    private static String label(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}

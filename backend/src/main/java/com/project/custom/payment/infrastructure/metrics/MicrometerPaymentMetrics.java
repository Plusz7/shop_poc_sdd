package com.project.custom.payment.infrastructure.metrics;

import com.project.custom.payment.application.PaymentMetrics;
import com.project.custom.payment.application.PaymentOutcome;
import com.project.custom.payment.application.WebhookOutcome;
import com.project.custom.shared.infrastructure.metrics.AfterCommit;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * {@link PaymentMetrics} on Micrometer; names, lower-case labels and buckets from contracts/metrics.md §2.
 * Every label combination is registered with {@code 0} at startup.
 */
@Component
class MicrometerPaymentMetrics implements PaymentMetrics {

    static final String WEBHOOK = "shop.payment.webhook";
    static final String PAYMENTS = "shop.payments";
    static final String CONFIRMATION_DELAY = "shop.payment.confirmation.delay";

    private final Map<WebhookOutcome, Counter> webhooks = new EnumMap<>(WebhookOutcome.class);
    private final Map<PaymentOutcome, Counter> payments = new EnumMap<>(PaymentOutcome.class);
    private final Timer confirmationDelay;

    MicrometerPaymentMetrics(MeterRegistry registry) {
        for (WebhookOutcome outcome : WebhookOutcome.values()) {
            webhooks.put(outcome, Counter.builder(WEBHOOK)
                    .description("Stripe webhook calls by handling outcome")
                    .tag("outcome", label(outcome))
                    .register(registry));
        }
        for (PaymentOutcome outcome : PaymentOutcome.values()) {
            payments.put(outcome, Counter.builder(PAYMENTS)
                    .description("Payment outcomes reported by Stripe")
                    .tag("outcome", label(outcome))
                    .register(registry));
        }
        this.confirmationDelay = Timer.builder(CONFIRMATION_DELAY)
                .description("Time from a Stripe event to the commit of its handling")
                .serviceLevelObjectives(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(10),
                        Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(120))
                .register(registry);
    }

    @Override
    public void webhook(WebhookOutcome outcome) {
        Counter counter = webhooks.get(outcome);
        AfterCommit.run(counter::increment);
    }

    @Override
    public void payment(PaymentOutcome outcome) {
        Counter counter = payments.get(outcome);
        AfterCommit.run(counter::increment);
    }

    @Override
    public void confirmationDelay(Duration delay) {
        AfterCommit.run(() -> confirmationDelay.record(delay.isNegative() ? Duration.ZERO : delay));
    }

    private static String label(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}

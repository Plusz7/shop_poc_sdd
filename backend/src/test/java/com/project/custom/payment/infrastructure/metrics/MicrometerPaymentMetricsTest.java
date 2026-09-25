package com.project.custom.payment.infrastructure.metrics;

import com.project.custom.payment.application.PaymentOutcome;
import com.project.custom.payment.application.WebhookOutcome;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerPaymentMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerPaymentMetrics metrics = new MicrometerPaymentMetrics(registry);

    @Test
    void everyCombinationIsRegisteredWithZeroAtStartup() {
        for (String outcome : new String[]{"processed", "duplicate", "rejected_signature", "rejected_livemode",
                "ignored"}) {
            assertThat(webhooks(outcome)).isZero();
        }
        for (String outcome : new String[]{"succeeded", "declined", "canceled"}) {
            assertThat(payments(outcome)).isZero();
        }
        assertThat(registry.get("shop.payment.webhook").counters()).hasSize(5);
        assertThat(registry.get("shop.payments").counters()).hasSize(3);
        assertThat(confirmationDelay().count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"PROCESSED, processed", "DUPLICATE, duplicate", "REJECTED_SIGNATURE, rejected_signature",
            "REJECTED_LIVEMODE, rejected_livemode", "IGNORED, ignored"})
    void webhookOutcomeIsALowerCaseLabel(WebhookOutcome outcome, String label) {
        metrics.webhook(outcome);

        assertThat(webhooks(label)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"SUCCEEDED, succeeded", "DECLINED, declined", "CANCELED, canceled"})
    void paymentOutcomeIsALowerCaseLabel(PaymentOutcome outcome, String label) {
        metrics.payment(outcome);

        assertThat(payments(label)).isEqualTo(1);
    }

    @Test
    void confirmationDelayIsRecordedWithTheContractBuckets() {
        metrics.confirmationDelay(Duration.ofSeconds(12));

        assertThat(confirmationDelay().count()).isEqualTo(1);
        assertThat(confirmationDelay().totalTime(TimeUnit.SECONDS)).isEqualTo(12);
        assertThat(Arrays.stream(confirmationDelay().takeSnapshot().histogramCounts())
                .map(bucket -> bucket.bucket(TimeUnit.SECONDS)))
                .containsExactly(1.0, 5.0, 10.0, 30.0, 60.0, 120.0);
    }

    private double webhooks(String outcome) {
        return registry.get("shop.payment.webhook").tag("outcome", outcome).counter().count();
    }

    private double payments(String outcome) {
        return registry.get("shop.payments").tag("outcome", outcome).counter().count();
    }

    private Timer confirmationDelay() {
        return registry.get("shop.payment.confirmation.delay").timer();
    }
}

package com.project.custom.order.infrastructure.metrics;

import com.project.custom.order.application.MismatchKind;
import com.project.custom.order.domain.OrderStatus;
import com.project.custom.shared.domain.Money;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MicrometerOrderMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerOrderMetrics metrics = new MicrometerOrderMetrics(registry);

    @Test
    void everyCombinationIsRegisteredWithZeroAtStartup() {
        assertThat(count("shop.orders.placed")).isZero();
        for (MismatchKind kind : MismatchKind.values()) {
            assertThat(registry.get("shop.orders.mismatches").tag("kind", kind.name()).counter().count()).isZero();
        }
        for (String status : new String[]{"PAID", "PAYMENT_FAILED", "NEEDS_REVIEW"}) {
            assertThat(completed(status)).isZero();
        }
        assertThat(count("shop.orders.paid.value")).isZero();
        assertThat(timeToPayment().count()).isZero();
    }

    @Test
    void createdOrderIncrementsTheCounter() {
        metrics.orderCreated();

        assertThat(count("shop.orders.placed")).isEqualTo(1);
    }

    @Test
    void mismatchIncrementsOncePerDetectedKind() {
        metrics.summaryMismatch(Set.of(MismatchKind.PRICE, MismatchKind.CONTENTS));

        assertThat(mismatches(MismatchKind.PRICE)).isEqualTo(1);
        assertThat(mismatches(MismatchKind.CONTENTS)).isEqualTo(1);
        assertThat(mismatches(MismatchKind.AVAILABILITY)).isZero();
    }

    @Test
    void paidOrderRecordsStatusValueAndTimeToPayment() {
        metrics.orderCompleted(OrderStatus.PAID, Money.pln(12_345), Duration.ofSeconds(90));

        assertThat(completed("PAID")).isEqualTo(1);
        assertThat(count("shop.orders.paid.value")).isEqualTo(123.45);
        assertThat(registry.get("shop.orders.paid.value").counter().getId().getBaseUnit()).isEqualTo("pln");
        assertThat(timeToPayment().count()).isEqualTo(1);
        assertThat(timeToPayment().totalTime(TimeUnit.SECONDS)).isEqualTo(90);
    }

    @Test
    void timeToPaymentHasTheContractBuckets() {
        metrics.orderCompleted(OrderStatus.PAID, Money.pln(100), Duration.ofSeconds(90));

        assertThat(Arrays.stream(timeToPayment().takeSnapshot().histogramCounts())
                .map(bucket -> bucket.bucket(TimeUnit.SECONDS)))
                .containsExactly(30.0, 60.0, 120.0, 300.0, 600.0, 1800.0);
        assertThat(Arrays.stream(timeToPayment().takeSnapshot().histogramCounts())
                .filter(bucket -> bucket.bucket(TimeUnit.SECONDS) == 120.0)
                .mapToDouble(CountAtBucket::count).sum()).isEqualTo(1);
    }

    @Test
    void failedOrReviewedOrderRecordsOnlyTheStatus() {
        metrics.orderCompleted(OrderStatus.PAYMENT_FAILED, Money.pln(5_000), Duration.ofSeconds(10));
        metrics.orderCompleted(OrderStatus.NEEDS_REVIEW, Money.pln(5_000), Duration.ofSeconds(10));

        assertThat(completed("PAYMENT_FAILED")).isEqualTo(1);
        assertThat(completed("NEEDS_REVIEW")).isEqualTo(1);
        assertThat(completed("PAID")).isZero();
        assertThat(count("shop.orders.paid.value")).isZero();
        assertThat(timeToPayment().count()).isZero();
    }

    @Test
    void awaitingPaymentIsNotATargetStatus() {
        assertThatThrownBy(() -> metrics.orderCompleted(OrderStatus.AWAITING_PAYMENT, Money.pln(1), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private double count(String name) {
        return registry.get(name).counter().count();
    }

    private double mismatches(MismatchKind kind) {
        return registry.get("shop.orders.mismatches").tag("kind", kind.name()).counter().count();
    }

    private double completed(String status) {
        return registry.get("shop.orders.completed").tag("status", status).counter().count();
    }

    private Timer timeToPayment() {
        return registry.get("shop.order.time.to.payment").timer();
    }
}

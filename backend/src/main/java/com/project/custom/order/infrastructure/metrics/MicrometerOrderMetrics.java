package com.project.custom.order.infrastructure.metrics;

import com.project.custom.order.application.MismatchKind;
import com.project.custom.order.application.OrderMetrics;
import com.project.custom.order.domain.OrderStatus;
import com.project.custom.shared.domain.Money;
import com.project.custom.shared.infrastructure.metrics.AfterCommit;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link OrderMetrics} on Micrometer; names, labels and buckets from contracts/metrics.md §2. Every label
 * combination is registered with {@code 0} at startup.
 */
@Component
class MicrometerOrderMetrics implements OrderMetrics {

    static final String PLACED = "shop.orders.placed";
    static final String MISMATCHES = "shop.orders.mismatches";
    static final String COMPLETED = "shop.orders.completed";
    static final String PAID_VALUE = "shop.orders.paid.value";
    static final String TIME_TO_PAYMENT = "shop.order.time.to.payment";

    /** Target states of {@code shop.orders.completed}. */
    static final List<OrderStatus> COMPLETED_STATUSES =
            List.of(OrderStatus.PAID, OrderStatus.PAYMENT_FAILED, OrderStatus.NEEDS_REVIEW);

    private static final double MINOR_UNITS_PER_PLN = 100.0;

    private final Counter created;
    private final Map<MismatchKind, Counter> mismatches = new EnumMap<>(MismatchKind.class);
    private final Map<OrderStatus, Counter> completed = new EnumMap<>(OrderStatus.class);
    private final Counter paidValue;
    private final Timer timeToPayment;

    MicrometerOrderMetrics(MeterRegistry registry) {
        this.created = Counter.builder(PLACED).description("Orders placed").register(registry);
        for (MismatchKind kind : MismatchKind.values()) {
            mismatches.put(kind, Counter.builder(MISMATCHES)
                    .description("Orders refused because the confirmed summary was outdated")
                    .tag("kind", kind.name())
                    .register(registry));
        }
        for (OrderStatus status : COMPLETED_STATUSES) {
            completed.put(status, Counter.builder(COMPLETED)
                    .description("Order transitions to a target status")
                    .tag("status", status.name())
                    .register(registry));
        }
        this.paidValue = Counter.builder(PAID_VALUE)
                .description("Value of paid orders; for presentation only")
                .baseUnit("pln")
                .register(registry);
        this.timeToPayment = Timer.builder(TIME_TO_PAYMENT)
                .description("Time from placing an order to its payment")
                .serviceLevelObjectives(Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(2),
                        Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofMinutes(30))
                .register(registry);
    }

    @Override
    public void orderCreated() {
        AfterCommit.run(created::increment);
    }

    @Override
    public void summaryMismatch(Set<MismatchKind> kinds) {
        Set<MismatchKind> recorded = Set.copyOf(kinds);
        AfterCommit.run(() -> recorded.forEach(kind -> mismatches.get(kind).increment()));
    }

    @Override
    public void orderCompleted(OrderStatus target, Money total, Duration timeToPayment) {
        Counter counter = completed.get(target);
        if (counter == null) {
            throw new IllegalArgumentException("Not a target status of an order: " + target);
        }
        AfterCommit.run(() -> {
            counter.increment();
            if (target == OrderStatus.PAID) {
                paidValue.increment(total.minor() / MINOR_UNITS_PER_PLN);
                this.timeToPayment.record(timeToPayment);
            }
        });
    }
}

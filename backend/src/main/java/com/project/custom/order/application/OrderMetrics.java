package com.project.custom.order.application;

import com.project.custom.order.domain.OrderStatus;
import com.project.custom.shared.domain.Money;

import java.time.Duration;
import java.util.Set;

/**
 * Business metrics of orders (research R-27, contracts/metrics.md). Calls made inside a transaction are
 * recorded only after its commit; calls outside a transaction immediately.
 */
public interface OrderMetrics {

    /** An order awaiting payment was stored. */
    void orderCreated();

    /** An order was refused with {@code 409 SUMMARY_OUTDATED}; one increment per kind. */
    void summaryMismatch(Set<MismatchKind> kinds);

    /**
     * The order made a transition to {@code PAID}, {@code PAYMENT_FAILED} or {@code NEEDS_REVIEW}.
     *
     * @param total         the order total; recorded as sales value only for {@code PAID}
     * @param timeToPayment from placing the order to its payment; recorded only for {@code PAID}
     */
    void orderCompleted(OrderStatus target, Money total, Duration timeToPayment);
}

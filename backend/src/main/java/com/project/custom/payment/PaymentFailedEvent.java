package com.project.custom.payment;

import java.util.Objects;
import java.util.UUID;

/**
 * The payment of an order failed or its session expired; the order is not paid. Handled synchronously by the
 * order context in the same transaction (R-02).
 */
public record PaymentFailedEvent(UUID orderId, Reason reason) {

    public PaymentFailedEvent {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(reason, "reason");
    }

    public enum Reason {
        EXPIRED,
        FAILED
    }
}

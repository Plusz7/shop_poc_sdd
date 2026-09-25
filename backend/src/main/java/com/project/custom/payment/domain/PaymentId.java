package com.project.custom.payment.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier of a payment attempt; also the base of the provider idempotency key (R-11).
 */
public record PaymentId(UUID value) {

    public PaymentId {
        Objects.requireNonNull(value, "value");
    }

    public static PaymentId random() {
        return new PaymentId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

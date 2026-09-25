package com.project.custom.payment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The provider confirmed the payment of an order. Published within the webhook transaction and handled
 * synchronously by the order context (R-02).
 *
 * @param amountMinor charged amount as reported by the provider, in minor units
 * @param currency    charged currency as reported by the provider (ISO code, lower case)
 */
public record PaymentConfirmedEvent(UUID orderId, long amountMinor, String currency, Instant confirmedAt) {

    public PaymentConfirmedEvent {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(confirmedAt, "confirmedAt");
    }
}

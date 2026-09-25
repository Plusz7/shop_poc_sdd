package com.project.custom.payment.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A verified provider event, independent of the provider SDK.
 *
 * @param sessionId         the provider session the event is about; {@code null} for events of other objects
 * @param paymentIntentId   the provider payment id, when known
 * @param amountMinor       charged amount in minor units, when the event carries it
 * @param paid              the provider reports the session as paid ({@code payment_status == paid})
 * @param providerCreatedAt when the provider created the event
 */
public record ProviderConfirmation(String eventId, String type, String sessionId, String paymentIntentId,
                                   Long amountMinor, String currency, boolean paid, boolean livemode,
                                   Instant providerCreatedAt) {

    public ProviderConfirmation {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
    }
}

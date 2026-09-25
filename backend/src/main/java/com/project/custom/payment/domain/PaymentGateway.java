package com.project.custom.payment.domain;

/**
 * Port to the hosted payment page provider (Principle V). Implementations are called outside database
 * transactions and handle timeouts and retries themselves.
 */
public interface PaymentGateway {

    /**
     * Creates a hosted payment session for the order.
     *
     * @return {@link PaymentSession}, or {@link GatewayUnavailable} when the provider could not be reached
     */
    SessionResult createSession(SessionRequest request);

    /** Expires an open provider session, so that it can no longer be paid (R-13). */
    ExpiryResult expire(String providerSessionId);
}

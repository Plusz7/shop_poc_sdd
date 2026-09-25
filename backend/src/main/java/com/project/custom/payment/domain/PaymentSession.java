package com.project.custom.payment.domain;

import java.util.Objects;

/**
 * An open hosted payment session.
 *
 * @param url the hosted payment page the customer is redirected to
 */
public record PaymentSession(String providerSessionId, String url) implements SessionResult {

    public PaymentSession {
        Objects.requireNonNull(providerSessionId, "providerSessionId");
        Objects.requireNonNull(url, "url");
    }
}

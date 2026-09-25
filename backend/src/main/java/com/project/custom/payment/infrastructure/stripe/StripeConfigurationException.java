package com.project.custom.payment.infrastructure.stripe;

import com.stripe.exception.StripeException;

/**
 * Stripe rejected a request in a way that retrying cannot fix (invalid request, authentication, permission):
 * a configuration or programming error. The message names only the error type, code and request id, never
 * the key, the Stripe message or customer data.
 */
class StripeConfigurationException extends RuntimeException {

    StripeConfigurationException(StripeOperation operation, StripeException cause) {
        super("Stripe rejected " + operation + ": " + cause.getClass().getSimpleName()
                + ", status=" + cause.getStatusCode() + ", code=" + cause.getCode()
                + ", requestId=" + cause.getRequestId());
    }
}

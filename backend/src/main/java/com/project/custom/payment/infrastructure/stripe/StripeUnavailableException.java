package com.project.custom.payment.infrastructure.stripe;

/**
 * All attempts of a Stripe call failed with a retryable error.
 */
class StripeUnavailableException extends RuntimeException {

    StripeUnavailableException(StripeOperation operation, CallOutcome lastOutcome) {
        super("Stripe " + operation + " unavailable, last outcome " + lastOutcome);
    }
}

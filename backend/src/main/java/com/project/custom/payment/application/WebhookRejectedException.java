package com.project.custom.payment.application;

/**
 * The webhook call is not a genuine test-mode Stripe event; nothing was changed. The message never contains
 * the body or the signature header.
 */
public class WebhookRejectedException extends RuntimeException {

    private final WebhookOutcome outcome;

    WebhookRejectedException(WebhookOutcome outcome, String reason) {
        super(reason);
        this.outcome = outcome;
    }

    /** {@code REJECTED_SIGNATURE} or {@code REJECTED_LIVEMODE}. */
    public WebhookOutcome outcome() {
        return outcome;
    }
}

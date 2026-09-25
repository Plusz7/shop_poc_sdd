package com.project.custom.payment.application;

/**
 * How a webhook call was handled; the value of the {@code outcome} metric label (contracts/metrics.md).
 */
public enum WebhookOutcome {
    PROCESSED,
    DUPLICATE,
    REJECTED_SIGNATURE,
    REJECTED_LIVEMODE,
    IGNORED
}

package com.project.custom.payment.application;

/**
 * Outcome of a payment attempt reported by the provider (R-28); the value of the {@code outcome} metric label
 * (contracts/metrics.md).
 */
public enum PaymentOutcome {
    SUCCEEDED,
    DECLINED,
    CANCELED
}

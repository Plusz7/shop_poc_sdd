package com.project.custom.payment.infrastructure.stripe;

/**
 * Outcome of one Stripe call attempt; the value of the {@code outcome} metric label (contracts/metrics.md).
 */
enum CallOutcome {
    SUCCESS,
    ERROR,
    TIMEOUT
}

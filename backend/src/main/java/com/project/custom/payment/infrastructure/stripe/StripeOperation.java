package com.project.custom.payment.infrastructure.stripe;

/**
 * Stripe calls made by the adapter; the value of the {@code operation} metric label (contracts/metrics.md).
 */
enum StripeOperation {
    CREATE_SESSION,
    EXPIRE_SESSION
}

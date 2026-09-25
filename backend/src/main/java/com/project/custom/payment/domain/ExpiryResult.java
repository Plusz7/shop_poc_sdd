package com.project.custom.payment.domain;

/**
 * Outcome of expiring a provider session.
 */
public enum ExpiryResult {
    /** The session can no longer be paid. */
    EXPIRED,
    /** The session had already been paid; the order must not be paid again (R-13). */
    ALREADY_PAID,
    /** The provider could not be reached; it is unknown whether the session is still open. */
    UNAVAILABLE
}

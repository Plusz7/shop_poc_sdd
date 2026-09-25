package com.project.custom.order.domain;

/**
 * What a payment confirmation did to the order.
 */
public enum ConfirmationOutcome {
    /** The order became {@code PAID}; stock was decreased. */
    PAID,
    /** The order became {@code NEEDS_REVIEW}; see {@link Order#reviewReason()}. */
    NEEDS_REVIEW,
    /** A repeated confirmation of a paid order: nothing changed (FR-020). */
    UNCHANGED
}

package com.project.custom.order.domain;

/**
 * Why a paid order needs a person to look at it before fulfillment.
 */
public enum ReviewReason {
    /** The payment was confirmed but the stock was no longer sufficient (R-16). */
    INSUFFICIENT_STOCK,
    /** The charged amount or currency differs from the order total (SC-004). */
    AMOUNT_MISMATCH,
    /** The payment was confirmed after the order had been marked as failed. */
    CONFIRMED_AFTER_FAILURE
}

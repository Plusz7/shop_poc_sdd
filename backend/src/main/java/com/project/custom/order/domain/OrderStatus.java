package com.project.custom.order.domain;

/**
 * Order lifecycle (data-model.md, state machine). {@code PAID} and {@code NEEDS_REVIEW} are final in the PoC.
 */
public enum OrderStatus {
    AWAITING_PAYMENT,
    PAID,
    PAYMENT_FAILED,
    NEEDS_REVIEW
}

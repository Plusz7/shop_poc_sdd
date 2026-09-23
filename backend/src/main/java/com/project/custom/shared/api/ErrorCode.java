package com.project.custom.shared.api;

/**
 * Error codes of the REST contract ({@code Problem.code} in openapi.yaml).
 */
public enum ErrorCode {
    VALIDATION_ERROR,
    NOT_FOUND,
    QUANTITY_EXCEEDS_LIMIT,
    PRODUCT_UNAVAILABLE,
    SUMMARY_OUTDATED,
    CART_NOT_ORDERABLE,
    ORDER_ALREADY_PAID,
    PAYMENT_UNAVAILABLE,
    CONCURRENCY_CONFLICT,
    INTERNAL_ERROR
}

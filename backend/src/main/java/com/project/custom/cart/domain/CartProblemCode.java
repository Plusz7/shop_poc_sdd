package com.project.custom.cart.domain;

/**
 * Why a priced cart line needs the customer's attention (FR-011, FR-014).
 */
public enum CartProblemCode {
    PRICE_CHANGED,
    PRODUCT_UNAVAILABLE,
    QUANTITY_EXCEEDS_STOCK
}

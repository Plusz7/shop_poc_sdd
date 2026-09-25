package com.project.custom.cart.application;

/**
 * Business metrics of the cart (research R-27, contracts/metrics.md). Called inside the use case transaction;
 * the implementation records only after commit.
 */
public interface CartMetrics {

    /** A line was added to a cart ({@code POST /api/cart/lines} succeeded). */
    void addedToCart();
}

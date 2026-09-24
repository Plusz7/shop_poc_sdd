package com.project.custom.cart.domain;

import com.project.custom.shared.domain.Money;

import java.time.Instant;
import java.util.Objects;

/**
 * A cart line — at most one per product.
 *
 * @param priceWhenAdded informational only, for the price change notice (R-09); never used for pricing (FR-010)
 */
public record CartLine(long productId, int quantity, Money priceWhenAdded, Instant addedAt) {

    public static final int MIN_QUANTITY = 1;
    public static final int MAX_QUANTITY = 99;

    public CartLine {
        Objects.requireNonNull(priceWhenAdded, "priceWhenAdded");
        Objects.requireNonNull(addedAt, "addedAt");
        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("Cart line quantity must be between 1 and 99");
        }
    }

    CartLine withQuantity(int newQuantity) {
        return new CartLine(productId, newQuantity, priceWhenAdded, addedAt);
    }
}

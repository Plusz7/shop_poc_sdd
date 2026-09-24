package com.project.custom.cart.domain;

/**
 * Adding would exceed what one cart line may hold (FR-008, US2-3); the cart is unchanged.
 */
public class QuantityExceedsLimitException extends RuntimeException {

    private final int maxQuantity;

    public QuantityExceedsLimitException(int maxQuantity) {
        super("Quantity exceeds the limit; at most " + maxQuantity + " more item(s) can be added");
        this.maxQuantity = maxQuantity;
    }

    /** How many items can still be added to the line (may be 0). */
    public int maxQuantity() {
        return maxQuantity;
    }
}

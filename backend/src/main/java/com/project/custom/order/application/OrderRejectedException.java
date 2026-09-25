package com.project.custom.order.application;

import com.project.custom.cart.PricedCartDto;

import java.util.Optional;

/**
 * The order could not be placed; the cart is unchanged.
 */
public class OrderRejectedException extends RuntimeException {

    private final Reason reason;
    private final PricedCartDto currentCart;

    OrderRejectedException(Reason reason, PricedCartDto currentCart) {
        super("Order rejected: " + reason);
        this.reason = reason;
        this.currentCart = currentCart;
    }

    OrderRejectedException(Reason reason) {
        this(reason, null);
    }

    public Reason reason() {
        return reason;
    }

    /** The freshly priced cart, for the customer to confirm again; present for cart-related reasons. */
    public Optional<PricedCartDto> currentCart() {
        return Optional.ofNullable(currentCart);
    }

    public enum Reason {
        /** The cart is empty or has lines that cannot be ordered (FR-014). */
        CART_NOT_ORDERABLE,
        /** Prices, quantities or contents changed since the customer saw the summary (FR-016, R-14). */
        SUMMARY_OUTDATED,
        /** The delivery details are invalid (FR-015). */
        INVALID_DETAILS,
        /** A previous payment session of the guest has already been paid (R-13). */
        ALREADY_PAID,
        /** The payment provider is unavailable (R-13). */
        PAYMENT_UNAVAILABLE
    }
}

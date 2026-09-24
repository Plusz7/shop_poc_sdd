package com.project.custom.cart.domain;

import com.project.custom.shared.domain.Money;

import java.util.Objects;

/**
 * The catalog product in the cart's language: what a cart line needs to be added and priced.
 *
 * @param maxQuantity how many items one cart line may hold ({@code min(stock, 99)}, 0 when unavailable)
 */
public record CartProduct(long productId, String name, String imageUrl, Money price, ProductAvailability availability,
                          int maxQuantity) {

    public CartProduct {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(imageUrl, "imageUrl");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(availability, "availability");
        if (maxQuantity < 0) {
            throw new IllegalArgumentException("Max quantity must not be negative");
        }
    }

    public boolean isAvailable() {
        return availability != ProductAvailability.UNAVAILABLE && maxQuantity > 0;
    }
}

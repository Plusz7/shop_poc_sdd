package com.project.custom.cart.domain;

import com.project.custom.shared.domain.Money;

/**
 * A cart line priced with the current catalog price (FR-010).
 */
public record PricedLine(long productId, String name, String imageUrl, Money unitPrice, int quantity,
                         Money lineTotal, ProductAvailability availability, int maxQuantity) {

    public boolean isAvailable() {
        return availability != ProductAvailability.UNAVAILABLE;
    }
}

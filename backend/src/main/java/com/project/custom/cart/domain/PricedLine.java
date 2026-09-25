package com.project.custom.cart.domain;

import com.project.custom.shared.domain.Money;

/**
 * A cart line priced with the current catalog price (FR-010).
 *
 * @param priceChanged         the current price differs from the price the customer last saw (FR-011, R-09)
 * @param previousPrice        the price the customer last saw, when {@code priceChanged}; otherwise {@code null}
 * @param quantityExceedsStock the line holds more items than are available now
 */
public record PricedLine(long productId, String name, String imageUrl, Money unitPrice, int quantity,
                         Money lineTotal, ProductAvailability availability, int maxQuantity, boolean priceChanged,
                         Money previousPrice, boolean quantityExceedsStock) {

    public boolean isAvailable() {
        return availability != ProductAvailability.UNAVAILABLE;
    }
}

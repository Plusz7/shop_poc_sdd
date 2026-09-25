package com.project.custom.order.domain;

import com.project.custom.shared.domain.Money;

import java.util.Objects;

/**
 * An immutable order line: a copy of the product name and price at the time of ordering (FR-017).
 *
 * @param productId informational reference to the catalog product, used to decrease the stock
 */
public record OrderLine(int lineNo, long productId, String name, Money unitPrice, int quantity) {

    public OrderLine {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (lineNo < 1) {
            throw new IllegalArgumentException("Line number must be positive");
        }
        if (unitPrice.minor() <= 0) {
            throw new IllegalArgumentException("Unit price must be positive");
        }
        if (quantity < 1 || quantity > 99) {
            throw new IllegalArgumentException("Quantity must be between 1 and 99");
        }
    }

    public Money lineTotal() {
        return unitPrice.times(quantity);
    }
}

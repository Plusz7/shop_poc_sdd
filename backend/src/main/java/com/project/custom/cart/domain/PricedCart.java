package com.project.custom.cart.domain;

import com.project.custom.shared.domain.Money;

import java.util.List;

/**
 * The cart priced with current catalog data (read model, not persisted).
 *
 * @param itemCount sum of the quantities of all lines (header counter, FR-013)
 * @param total     sum of the line totals of available lines (FR-010)
 */
public record PricedCart(List<PricedLine> lines, int itemCount, Money total) {

    private static final PricedCart EMPTY = new PricedCart(List.of(), 0, Money.ZERO);

    public PricedCart {
        lines = List.copyOf(lines);
    }

    public static PricedCart empty() {
        return EMPTY;
    }
}

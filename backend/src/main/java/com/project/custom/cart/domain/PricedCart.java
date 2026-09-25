package com.project.custom.cart.domain;

import com.project.custom.shared.domain.Money;

import java.util.List;

/**
 * The cart priced with current catalog data (read model, not persisted).
 *
 * @param itemCount     sum of the quantities of all lines (header counter, FR-013)
 * @param total         sum of the line totals of available lines (FR-010)
 * @param canPlaceOrder the cart is not empty and every line is available in the required quantity (FR-014)
 * @param problems      notices about the lines, in the order of the lines
 */
public record PricedCart(List<PricedLine> lines, int itemCount, Money total, boolean canPlaceOrder,
                         List<CartProblem> problems) {

    private static final PricedCart EMPTY = new PricedCart(List.of(), 0, Money.ZERO, false, List.of());

    public PricedCart {
        lines = List.copyOf(lines);
        problems = List.copyOf(problems);
    }

    public static PricedCart empty() {
        return EMPTY;
    }
}

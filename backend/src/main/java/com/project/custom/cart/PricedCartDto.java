package com.project.custom.cart;

import java.util.List;

/**
 * The cart priced with current catalog data (data-model.md, PricedCart).
 *
 * @param itemCount     sum of the quantities of all lines
 * @param totalMinor    sum of the line totals of available lines, in grosze
 * @param canPlaceOrder the cart is not empty and every line is available in the required quantity (FR-014)
 */
public record PricedCartDto(List<Line> lines, int itemCount, long totalMinor, boolean canPlaceOrder,
                            List<Problem> problems) {

    public PricedCartDto {
        lines = List.copyOf(lines);
        problems = List.copyOf(problems);
    }

    /**
     * @param status               {@code AVAILABLE | LOW_STOCK | UNAVAILABLE}
     * @param previousPriceMinor   the price the customer last saw, when {@code priceChanged}; otherwise {@code null}
     * @param quantityExceedsStock the line holds more items than are available now
     */
    public record Line(long productId, String name, String imageUrl, long unitPriceMinor, int quantity,
                       long lineTotalMinor, String status, int maxQuantity, boolean priceChanged,
                       Long previousPriceMinor, boolean quantityExceedsStock) {

        public boolean isAvailable() {
            return !"UNAVAILABLE".equals(status);
        }
    }

    /** @param code {@code PRICE_CHANGED | PRODUCT_UNAVAILABLE | QUANTITY_EXCEEDS_STOCK} */
    public record Problem(String code, long productId) {
    }
}

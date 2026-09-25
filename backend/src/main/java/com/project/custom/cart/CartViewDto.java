package com.project.custom.cart;

import java.util.List;

/**
 * The contract schema {@code Cart} (openapi.yaml).
 */
public record CartViewDto(List<Line> lines, int itemCount, long totalMinor, boolean canPlaceOrder,
                          List<Message> messages) {

    public CartViewDto {
        lines = List.copyOf(lines);
        messages = List.copyOf(messages);
    }

    /** The contract schema {@code CartLine}. */
    public record Line(long productId, String name, String imageUrl, long unitPriceMinor, int quantity,
                       long lineTotalMinor, String status, int maxQuantity, boolean priceChanged,
                       Long previousPriceMinor, boolean quantityExceedsStock) {
    }

    /** The contract schema {@code Message}; {@code text} is customer-facing copy. */
    public record Message(String code, Long productId, String text) {
    }
}

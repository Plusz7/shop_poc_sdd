package com.project.custom.catalog;

/**
 * A product as needed to price a cart or an order.
 *
 * @param status      {@code AVAILABLE | LOW_STOCK | UNAVAILABLE} (FR-005)
 * @param maxQuantity how many items one cart line may hold: {@code min(stock, 99)}, 0 when unavailable
 */
public record PricingProductDto(long productId, String name, String imageUrl, long priceMinor, int stock,
                                boolean active, String status, int maxQuantity) {
}

package com.project.custom.catalog;

/**
 * How many items of a product to take from the stock.
 */
public record StockLineDto(long productId, int quantity) {

    public StockLineDto {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }
}

package com.project.custom.catalog.domain;

import com.project.custom.shared.domain.Money;

import java.util.Objects;

/**
 * Read model of a product on the list: only active products are listed, each with its main image.
 */
public record ProductListItem(ProductId id, String name, Money price, int stock, ProductImage mainImage) {

    public ProductListItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(mainImage, "mainImage");
    }

    public AvailabilityStatus availabilityStatus() {
        return AvailabilityStatus.of(true, stock);
    }

    public int maxPurchasable() {
        return Product.maxPurchasable(true, stock);
    }
}

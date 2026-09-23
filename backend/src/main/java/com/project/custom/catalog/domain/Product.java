package com.project.custom.catalog.domain;

import com.project.custom.shared.domain.Money;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Catalog product aggregate. An inactive product is "removed from the catalog": it is neither listed
 * nor purchasable.
 */
public class Product {

    /** Upper limit of a single cart line (FR-008). */
    public static final int MAX_QUANTITY_PER_LINE = 99;

    private final ProductId id;
    private final String name;
    private final String description;
    private final Money price;
    private final CategoryId categoryId;
    private final boolean active;
    private final List<ProductImage> images;
    private int stock;

    public Product(ProductId id, String name, String description, Money price, CategoryId categoryId, int stock,
                   boolean active, List<ProductImage> images) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.description = description == null ? "" : description;
        this.price = Objects.requireNonNull(price, "price");
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId");
        if (stock < 0) {
            throw new IllegalArgumentException("Stock must not be negative");
        }
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("Product must have at least one image");
        }
        this.stock = stock;
        this.active = active;
        this.images = images.stream().sorted(Comparator.comparingInt(ProductImage::displayOrder)).toList();
    }

    /** Maximum quantity a customer can hold in a cart line; 0 when the product cannot be bought. */
    static int maxPurchasable(boolean active, int stock) {
        return active ? Math.min(stock, MAX_QUANTITY_PER_LINE) : 0;
    }

    public AvailabilityStatus availabilityStatus() {
        return AvailabilityStatus.of(active, stock);
    }

    public int maxPurchasable() {
        return maxPurchasable(active, stock);
    }

    public boolean canDecreaseStock(int quantity) {
        return quantity > 0 && quantity <= stock;
    }

    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to decrease must be positive");
        }
        if (quantity > stock) {
            throw new IllegalStateException("Insufficient stock of product " + id.value());
        }
        stock -= quantity;
    }

    public ProductImage mainImage() {
        return images.getFirst();
    }

    public ProductId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Money price() {
        return price;
    }

    public CategoryId categoryId() {
        return categoryId;
    }

    public int stock() {
        return stock;
    }

    public boolean active() {
        return active;
    }

    public List<ProductImage> images() {
        return images;
    }
}

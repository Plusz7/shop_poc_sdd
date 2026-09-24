package com.project.custom.cart.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cart_line")
@IdClass(CartLineJpaEntity.Key.class)
class CartLineJpaEntity {

    @Id
    @Column(name = "cart_id")
    private UUID cartId;

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "price_when_added_minor", nullable = false)
    private long priceWhenAddedMinor;

    @Column(name = "added_at", nullable = false)
    private OffsetDateTime addedAt;

    protected CartLineJpaEntity() {
    }

    CartLineJpaEntity(UUID cartId, Long productId, int quantity, long priceWhenAddedMinor, OffsetDateTime addedAt) {
        this.cartId = cartId;
        this.productId = productId;
        this.quantity = quantity;
        this.priceWhenAddedMinor = priceWhenAddedMinor;
        this.addedAt = addedAt;
    }

    Long getProductId() {
        return productId;
    }

    int getQuantity() {
        return quantity;
    }

    void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    long getPriceWhenAddedMinor() {
        return priceWhenAddedMinor;
    }

    void setPriceWhenAddedMinor(long priceWhenAddedMinor) {
        this.priceWhenAddedMinor = priceWhenAddedMinor;
    }

    OffsetDateTime getAddedAt() {
        return addedAt;
    }

    record Key(UUID cartId, Long productId) implements Serializable {
    }
}

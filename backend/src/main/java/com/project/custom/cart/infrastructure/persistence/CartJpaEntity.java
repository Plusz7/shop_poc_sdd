package com.project.custom.cart.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "cart")
class CartJpaEntity {

    @Id
    private UUID id;

    @Column(name = "guest_id", nullable = false, unique = true)
    private UUID guestId;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** {@code null} until the cart is first stored, so that Spring Data persists (not merges) a new cart. */
    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "cart_id", insertable = false, updatable = false)
    @OrderBy("addedAt ASC, productId ASC")
    private List<CartLineJpaEntity> lines = new ArrayList<>();

    protected CartJpaEntity() {
    }

    CartJpaEntity(UUID id, UUID guestId) {
        this.id = id;
        this.guestId = guestId;
    }

    UUID getId() {
        return id;
    }

    UUID getGuestId() {
        return guestId;
    }

    OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    long getVersion() {
        return version == null ? 0 : version;
    }

    List<CartLineJpaEntity> getLines() {
        return lines;
    }
}

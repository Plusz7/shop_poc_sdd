package com.project.custom.cart.domain;

import com.project.custom.shared.domain.GuestId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The guest's cart aggregate (one per {@link GuestId}). Lines keep the order in which products were added.
 */
public class Cart {

    private final CartId id;
    private final GuestId guestId;
    private final List<CartLine> lines;
    private final long version;
    private Instant updatedAt;

    private Cart(CartId id, GuestId guestId, List<CartLine> lines, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.guestId = Objects.requireNonNull(guestId, "guestId");
        this.lines = new ArrayList<>(lines);
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
        if (lines.stream().map(CartLine::productId).distinct().count() != lines.size()) {
            throw new IllegalArgumentException("A cart holds at most one line per product");
        }
    }

    public static Cart create(CartId id, GuestId guestId, Instant now) {
        return new Cart(id, guestId, List.of(), now, 0);
    }

    public static Cart restore(CartId id, GuestId guestId, List<CartLine> lines, Instant updatedAt, long version) {
        return new Cart(id, guestId, lines, updatedAt, version);
    }

    /**
     * Adds {@code quantity} items of the product, merging with an existing line (FR-007).
     *
     * @throws ProductUnavailableException    when the product is unavailable (US2-4)
     * @throws QuantityExceedsLimitException when the line would exceed {@code product.maxQuantity} (FR-008)
     */
    public void add(CartProduct product, int quantity, Instant now) {
        if (quantity < CartLine.MIN_QUANTITY || quantity > CartLine.MAX_QUANTITY) {
            throw new IllegalArgumentException("Quantity to add must be between 1 and 99");
        }
        if (!product.isAvailable()) {
            throw new ProductUnavailableException(product.productId());
        }
        Optional<CartLine> existing = line(product.productId());
        int current = existing.map(CartLine::quantity).orElse(0);
        int limit = Math.min(product.maxQuantity(), CartLine.MAX_QUANTITY);
        if (current + quantity > limit) {
            throw new QuantityExceedsLimitException(Math.max(0, limit - current));
        }
        existing.ifPresentOrElse(
                line -> lines.set(lines.indexOf(line), line.withQuantity(current + quantity)),
                () -> lines.add(new CartLine(product.productId(), quantity, product.price(), now)));
        updatedAt = now;
    }

    public Optional<CartLine> line(long productId) {
        return lines.stream().filter(line -> line.productId() == productId).findFirst();
    }

    public Set<Long> productIds() {
        return lines.stream().map(CartLine::productId).collect(Collectors.toUnmodifiableSet());
    }

    public List<CartLine> lines() {
        return List.copyOf(lines);
    }

    public CartId id() {
        return id;
    }

    public GuestId guestId() {
        return guestId;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /** Optimistic locking version of the loaded state (two tabs of the same guest). */
    public long version() {
        return version;
    }
}

package com.project.custom.cart.domain;

import com.project.custom.shared.domain.GuestId;
import com.project.custom.shared.domain.Money;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    /**
     * Sets the quantity of the product's line; {@code 0} removes the line (US3-4). A quantity above
     * {@code maxQuantity} is capped at it (US3-3, FR-008).
     *
     * @param maxQuantity how many items the line may hold now ({@code min(stock, 99)}, 0 when unavailable)
     * @return the cap applied, when the requested quantity was larger than allowed
     * @throws CartLineNotFoundException  when the cart has no line for the product
     * @throws ProductUnavailableException when a positive quantity is set for an unavailable product
     */
    public Optional<QuantityCapped> changeQuantity(long productId, int quantity, int maxQuantity, Instant now) {
        if (quantity < 0 || quantity > CartLine.MAX_QUANTITY) {
            throw new IllegalArgumentException("Quantity must be between 0 and 99");
        }
        CartLine line = line(productId).orElseThrow(() -> new CartLineNotFoundException(productId));
        if (quantity == 0) {
            remove(productId, now);
            return Optional.empty();
        }
        int limit = Math.min(maxQuantity, CartLine.MAX_QUANTITY);
        if (limit < CartLine.MIN_QUANTITY) {
            throw new ProductUnavailableException(productId);
        }
        int newQuantity = Math.min(quantity, limit);
        lines.set(lines.indexOf(line), line.withQuantity(newQuantity));
        updatedAt = now;
        return newQuantity < quantity ? Optional.of(new QuantityCapped(limit)) : Optional.empty();
    }

    /** Removes the product's line; removing a line that is not in the cart changes nothing. */
    public void remove(long productId, Instant now) {
        if (lines.removeIf(line -> line.productId() == productId)) {
            updatedAt = now;
        }
    }

    /** Removes all lines (FR-009, FR-021). */
    public void clear(Instant now) {
        lines.clear();
        updatedAt = now;
    }

    /**
     * The customer acknowledged the current prices: they become the reference for price change notices (R-09).
     *
     * @param currentPrices current catalog prices by product id; lines of other products are unchanged
     */
    public void acceptPrices(Map<Long, Money> currentPrices, Instant now) {
        lines.replaceAll(line -> {
            Money current = currentPrices.get(line.productId());
            return current == null ? line : line.withPriceWhenAdded(current);
        });
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

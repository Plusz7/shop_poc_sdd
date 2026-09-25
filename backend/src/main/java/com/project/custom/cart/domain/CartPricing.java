package com.project.custom.cart.domain;

import com.project.custom.shared.domain.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Prices a cart with current catalog data; prices stored in the cart or sent by the client are never used
 * (FR-010, Principle II).
 */
public final class CartPricing {

    private CartPricing() {
    }

    /**
     * @param products current catalog data by product id; a line whose product no longer exists in the catalog
     *                 is kept as unavailable (edge case "product removed from the catalog")
     */
    public static PricedCart price(Cart cart, Map<Long, CartProduct> products) {
        List<PricedLine> lines = new ArrayList<>();
        List<CartProblem> problems = new ArrayList<>();
        int itemCount = 0;
        Money total = Money.ZERO;
        boolean everyLineOrderable = true;
        for (CartLine line : cart.lines()) {
            CartProduct product = products.get(line.productId());
            PricedLine priced = product == null ? removedFromCatalog(line) : priced(line, product);
            lines.add(priced);
            itemCount += priced.quantity();
            if (priced.isAvailable()) {
                total = total.plus(priced.lineTotal());
            }
            if (priced.priceChanged()) {
                problems.add(new CartProblem(CartProblemCode.PRICE_CHANGED, priced.productId()));
            }
            if (!priced.isAvailable()) {
                problems.add(new CartProblem(CartProblemCode.PRODUCT_UNAVAILABLE, priced.productId()));
            }
            if (priced.quantityExceedsStock()) {
                problems.add(new CartProblem(CartProblemCode.QUANTITY_EXCEEDS_STOCK, priced.productId()));
            }
            everyLineOrderable &= priced.isAvailable() && !priced.quantityExceedsStock();
        }
        return new PricedCart(lines, itemCount, total, !lines.isEmpty() && everyLineOrderable, problems);
    }

    private static PricedLine priced(CartLine line, CartProduct product) {
        boolean available = product.isAvailable();
        boolean priceChanged = !product.price().equals(line.priceWhenAdded());
        return new PricedLine(line.productId(), product.name(), product.imageUrl(), product.price(),
                line.quantity(), product.price().times(line.quantity()),
                available ? product.availability() : ProductAvailability.UNAVAILABLE,
                available ? product.maxQuantity() : 0,
                priceChanged, priceChanged ? line.priceWhenAdded() : null,
                available && line.quantity() > product.maxQuantity());
    }

    /** The catalog no longer knows the product: only what the cart line remembers can be shown. */
    private static PricedLine removedFromCatalog(CartLine line) {
        return new PricedLine(line.productId(), "", "", line.priceWhenAdded(), line.quantity(),
                line.priceWhenAdded().times(line.quantity()), ProductAvailability.UNAVAILABLE, 0, false, null, false);
    }
}

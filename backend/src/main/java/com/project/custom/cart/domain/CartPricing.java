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
     *                 is left out
     */
    public static PricedCart price(Cart cart, Map<Long, CartProduct> products) {
        List<PricedLine> lines = new ArrayList<>();
        int itemCount = 0;
        Money total = Money.ZERO;
        for (CartLine line : cart.lines()) {
            CartProduct product = products.get(line.productId());
            if (product == null) {
                continue;
            }
            PricedLine priced = new PricedLine(line.productId(), product.name(), product.imageUrl(),
                    product.price(), line.quantity(), product.price().times(line.quantity()),
                    product.availability(), product.maxQuantity());
            lines.add(priced);
            itemCount += priced.quantity();
            if (priced.isAvailable()) {
                total = total.plus(priced.lineTotal());
            }
        }
        return new PricedCart(lines, itemCount, total);
    }
}

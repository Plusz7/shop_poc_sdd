package com.project.custom.cart.domain;

import com.project.custom.shared.domain.GuestId;
import com.project.custom.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CartPricingTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    @Test
    void emptyCartHasNoLinesAndZeroTotals() {
        PricedCart priced = CartPricing.price(Cart.create(CartId.random(), GuestId.random(), NOW), Map.of());

        assertThat(priced.lines()).isEmpty();
        assertThat(priced.itemCount()).isZero();
        assertThat(priced.total()).isEqualTo(Money.ZERO);
        assertThat(PricedCart.empty()).isEqualTo(priced);
    }

    @Test
    void pricesLinesWithTheCurrentCatalogPrice() {
        CartProduct mug = product(1, 4_990, ProductAvailability.AVAILABLE, 20);
        Cart cart = Cart.create(CartId.random(), GuestId.random(), NOW);
        cart.add(mug, 3, NOW);

        CartProduct mugAfterPriceChange = product(1, 5_490, ProductAvailability.AVAILABLE, 20);
        PricedCart priced = CartPricing.price(cart, Map.of(1L, mugAfterPriceChange));

        assertThat(priced.lines()).singleElement().satisfies(line -> {
            assertThat(line.productId()).isEqualTo(1);
            assertThat(line.name()).isEqualTo("Product 1");
            assertThat(line.imageUrl()).isEqualTo("/images/1.svg");
            assertThat(line.unitPrice()).isEqualTo(Money.pln(5_490));
            assertThat(line.quantity()).isEqualTo(3);
            assertThat(line.lineTotal()).isEqualTo(Money.pln(16_470));
            assertThat(line.availability()).isEqualTo(ProductAvailability.AVAILABLE);
            assertThat(line.maxQuantity()).isEqualTo(20);
        });
        assertThat(priced.total()).isEqualTo(Money.pln(16_470));
    }

    @Test
    void itemCountSumsQuantitiesAndTotalSumsLineTotalsOfAvailableLines() {
        Cart cart = Cart.create(CartId.random(), GuestId.random(), NOW);
        cart.add(product(1, 1_000, ProductAvailability.AVAILABLE, 10), 2, NOW);
        cart.add(product(2, 2_500, ProductAvailability.LOW_STOCK, 3), 3, NOW);
        cart.add(product(3, 9_900, ProductAvailability.AVAILABLE, 10), 1, NOW);

        PricedCart priced = CartPricing.price(cart, Map.of(
                1L, product(1, 1_000, ProductAvailability.AVAILABLE, 10),
                2L, product(2, 2_500, ProductAvailability.LOW_STOCK, 3),
                3L, product(3, 9_900, ProductAvailability.UNAVAILABLE, 0)));

        assertThat(priced.itemCount()).isEqualTo(6);
        assertThat(priced.lines()).extracting(PricedLine::productId).containsExactly(1L, 2L, 3L);
        assertThat(priced.total()).isEqualTo(Money.pln(2 * 1_000 + 3 * 2_500));
    }

    private static CartProduct product(long id, long priceMinor, ProductAvailability availability, int maxQuantity) {
        return new CartProduct(id, "Product " + id, "/images/" + id + ".svg", Money.pln(priceMinor), availability,
                maxQuantity);
    }
}

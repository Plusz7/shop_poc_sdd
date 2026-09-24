package com.project.custom.cart.domain;

import com.project.custom.shared.domain.GuestId;
import com.project.custom.shared.domain.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartTest {

    private static final Instant CREATED = Instant.parse("2026-09-24T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-09-24T10:05:00Z");

    private final Cart cart = Cart.create(CartId.random(), GuestId.random(), CREATED);

    @Test
    void newCartIsEmpty() {
        assertThat(cart.lines()).isEmpty();
        assertThat(cart.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void addingCreatesLineWithPriceWhenAddedAndTimestamps() {
        cart.add(product(7, 12_900, ProductAvailability.AVAILABLE, 10), 2, LATER);

        assertThat(cart.lines()).containsExactly(new CartLine(7, 2, Money.pln(12_900), LATER));
        assertThat(cart.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void addingSameProductAgainMergesIntoOneLine() {
        cart.add(product(7, 12_900, ProductAvailability.AVAILABLE, 10), 2, CREATED);
        cart.add(product(7, 12_900, ProductAvailability.AVAILABLE, 10), 1, LATER);

        assertThat(cart.lines()).singleElement().satisfies(line -> {
            assertThat(line.quantity()).isEqualTo(3);
            assertThat(line.addedAt()).isEqualTo(CREATED);
        });
        assertThat(cart.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void mergingKeepsTheOriginalPriceWhenAdded() {
        cart.add(product(7, 12_900, ProductAvailability.AVAILABLE, 10), 1, CREATED);
        cart.add(product(7, 14_900, ProductAvailability.AVAILABLE, 10), 1, LATER);

        assertThat(cart.lines().getFirst().priceWhenAdded()).isEqualTo(Money.pln(12_900));
    }

    @Test
    void linesKeepTheOrderInWhichProductsWereAdded() {
        cart.add(product(9, 1_000, ProductAvailability.AVAILABLE, 10), 1, CREATED);
        cart.add(product(3, 1_000, ProductAvailability.AVAILABLE, 10), 1, LATER);
        cart.add(product(9, 1_000, ProductAvailability.AVAILABLE, 10), 1, LATER);

        assertThat(cart.lines()).extracting(CartLine::productId).containsExactly(9L, 3L);
    }

    @Test
    void addingUpToTheLimitIsAllowed() {
        cart.add(product(7, 1_000, ProductAvailability.LOW_STOCK, 3), 2, CREATED);
        cart.add(product(7, 1_000, ProductAvailability.LOW_STOCK, 3), 1, LATER);

        assertThat(cart.lines().getFirst().quantity()).isEqualTo(3);
    }

    @Test
    void exceedingTheLimitIsRejectedWithRemainingQuantityAndNoChange() {
        CartProduct stockOfThree = product(7, 1_000, ProductAvailability.LOW_STOCK, 3);
        cart.add(stockOfThree, 2, CREATED);

        assertThatThrownBy(() -> cart.add(stockOfThree, 2, LATER))
                .isInstanceOfSatisfying(QuantityExceedsLimitException.class,
                        exception -> assertThat(exception.maxQuantity()).isEqualTo(1));
        assertThat(cart.lines().getFirst().quantity()).isEqualTo(2);
        assertThat(cart.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void lineAlreadyAtTheLimitReportsZeroRemaining() {
        CartProduct stockOfTwo = product(7, 1_000, ProductAvailability.LOW_STOCK, 2);
        cart.add(stockOfTwo, 2, CREATED);

        assertThatThrownBy(() -> cart.add(stockOfTwo, 1, LATER))
                .isInstanceOfSatisfying(QuantityExceedsLimitException.class,
                        exception -> assertThat(exception.maxQuantity()).isZero());
    }

    @Test
    void lineNeverExceeds99Items() {
        CartProduct plenty = product(7, 1_000, ProductAvailability.AVAILABLE, 99);
        cart.add(plenty, 99, CREATED);

        assertThatThrownBy(() -> cart.add(plenty, 1, LATER)).isInstanceOf(QuantityExceedsLimitException.class);
    }

    @Test
    void unavailableProductCannotBeAdded() {
        assertThatThrownBy(() -> cart.add(product(7, 1_000, ProductAvailability.UNAVAILABLE, 0), 1, LATER))
                .isInstanceOf(ProductUnavailableException.class);
        assertThat(cart.lines()).isEmpty();
        assertThat(cart.updatedAt()).isEqualTo(CREATED);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 100})
    void quantityToAddMustBeBetween1And99(int quantity) {
        assertThatThrownBy(() -> cart.add(product(7, 1_000, ProductAvailability.AVAILABLE, 99), quantity, LATER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(cart.lines()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100})
    void cartLineQuantityIsBetween1And99(int quantity) {
        assertThatThrownBy(() -> new CartLine(7, quantity, Money.pln(1_000), CREATED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static CartProduct product(long id, long priceMinor, ProductAvailability availability, int maxQuantity) {
        return new CartProduct(id, "Product " + id, "/images/" + id + ".svg", Money.pln(priceMinor), availability,
                maxQuantity);
    }
}

package com.project.custom.cart.domain;

import com.project.custom.shared.domain.GuestId;
import com.project.custom.shared.domain.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

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

    @Test
    void changingQuantitySetsTheNewQuantityAndMovesUpdatedAt() {
        cart.add(product(7, 1_000, ProductAvailability.AVAILABLE, 10), 1, CREATED);

        Optional<QuantityCapped> capped = cart.changeQuantity(7, 3, 10, LATER);

        assertThat(capped).isEmpty();
        assertThat(cart.line(7)).get().extracting(CartLine::quantity).isEqualTo(3);
        assertThat(cart.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void changingQuantityToZeroRemovesTheLine() {
        cart.add(product(7, 1_000, ProductAvailability.AVAILABLE, 10), 2, CREATED);

        Optional<QuantityCapped> capped = cart.changeQuantity(7, 0, 10, LATER);

        assertThat(capped).isEmpty();
        assertThat(cart.lines()).isEmpty();
        assertThat(cart.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void quantityAboveTheLimitIsCappedAtTheLimit() {
        cart.add(product(7, 1_000, ProductAvailability.LOW_STOCK, 5), 1, CREATED);

        Optional<QuantityCapped> capped = cart.changeQuantity(7, 50, 5, LATER);

        assertThat(capped).contains(new QuantityCapped(5));
        assertThat(cart.line(7)).get().extracting(CartLine::quantity).isEqualTo(5);
    }

    @Test
    void quantityUpTo99IsAllowedWhenTheStockIsLarger() {
        cart.add(product(7, 1_000, ProductAvailability.AVAILABLE, 99), 1, CREATED);

        assertThat(cart.changeQuantity(7, 99, 500, LATER)).isEmpty();
        assertThat(cart.line(7)).get().extracting(CartLine::quantity).isEqualTo(99);
    }

    @Test
    void changingTheQuantityOfAnUnavailableProductIsRejectedButZeroStillRemovesIt() {
        cart.add(product(7, 1_000, ProductAvailability.AVAILABLE, 10), 2, CREATED);

        assertThatThrownBy(() -> cart.changeQuantity(7, 1, 0, LATER)).isInstanceOf(ProductUnavailableException.class);
        assertThat(cart.line(7)).get().extracting(CartLine::quantity).isEqualTo(2);

        cart.changeQuantity(7, 0, 0, LATER);
        assertThat(cart.lines()).isEmpty();
    }

    @Test
    void changingTheQuantityOfAMissingLineFails() {
        assertThatThrownBy(() -> cart.changeQuantity(7, 1, 10, LATER)).isInstanceOf(CartLineNotFoundException.class);
        assertThat(cart.updatedAt()).isEqualTo(CREATED);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 100})
    void newQuantityMustBeBetween0And99(int quantity) {
        cart.add(product(7, 1_000, ProductAvailability.AVAILABLE, 10), 2, CREATED);

        assertThatThrownBy(() -> cart.changeQuantity(7, quantity, 10, LATER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(cart.line(7)).get().extracting(CartLine::quantity).isEqualTo(2);
    }

    @Test
    void removingDeletesTheLine() {
        cart.add(product(7, 1_000, ProductAvailability.AVAILABLE, 10), 1, CREATED);
        cart.add(product(8, 1_000, ProductAvailability.AVAILABLE, 10), 1, CREATED);

        cart.remove(7, LATER);

        assertThat(cart.lines()).extracting(CartLine::productId).containsExactly(8L);
        assertThat(cart.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void removingAMissingLineIsANoOp() {
        cart.add(product(7, 1_000, ProductAvailability.AVAILABLE, 10), 1, CREATED);

        cart.remove(8, LATER);

        assertThat(cart.lines()).hasSize(1);
        assertThat(cart.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void clearingRemovesAllLines() {
        cart.add(product(7, 1_000, ProductAvailability.AVAILABLE, 10), 1, CREATED);
        cart.add(product(8, 1_000, ProductAvailability.AVAILABLE, 10), 1, CREATED);

        cart.clear(LATER);

        assertThat(cart.lines()).isEmpty();
        assertThat(cart.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void acceptingPricesOverwritesThePriceWhenAddedOfTheGivenProducts() {
        cart.add(product(7, 1_000, ProductAvailability.AVAILABLE, 10), 1, CREATED);
        cart.add(product(8, 2_000, ProductAvailability.AVAILABLE, 10), 1, CREATED);

        cart.acceptPrices(Map.of(7L, Money.pln(1_200)), LATER);

        assertThat(cart.line(7)).get().extracting(CartLine::priceWhenAdded).isEqualTo(Money.pln(1_200));
        assertThat(cart.line(8)).get().extracting(CartLine::priceWhenAdded).isEqualTo(Money.pln(2_000));
        assertThat(cart.line(7)).get().extracting(CartLine::addedAt).isEqualTo(CREATED);
        assertThat(cart.updatedAt()).isEqualTo(LATER);
    }

    private static CartProduct product(long id, long priceMinor, ProductAvailability availability, int maxQuantity) {
        return new CartProduct(id, "Product " + id, "/images/" + id + ".svg", Money.pln(priceMinor), availability,
                maxQuantity);
    }
}

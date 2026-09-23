package com.project.custom.catalog.domain;

import com.project.custom.shared.domain.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    @ParameterizedTest
    @CsvSource({
            "true, 0, UNAVAILABLE",
            "true, 1, LOW_STOCK",
            "true, 3, LOW_STOCK",
            "true, 4, AVAILABLE",
            "true, 500, AVAILABLE",
            "false, 10, UNAVAILABLE",
            "false, 0, UNAVAILABLE"
    })
    void derivesAvailabilityStatusFromActiveFlagAndStock(boolean active, int stock, AvailabilityStatus expected) {
        assertThat(product(stock, active).availabilityStatus()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "1, 1", "5, 5", "99, 99", "100, 99", "1000, 99"})
    void maxPurchasableIsStockCappedAt99(int stock, int expected) {
        assertThat(product(stock, true).maxPurchasable()).isEqualTo(expected);
    }

    @Test
    void inactiveProductCannotBePurchasedAtAll() {
        assertThat(product(10, false).maxPurchasable()).isZero();
    }

    @Test
    void canDecreaseStockOnlyWithinAvailableStock() {
        Product product = product(3, true);

        assertThat(product.canDecreaseStock(1)).isTrue();
        assertThat(product.canDecreaseStock(3)).isTrue();
        assertThat(product.canDecreaseStock(4)).isFalse();
    }

    @Test
    void decreaseStockReducesStock() {
        Product product = product(5, true);

        product.decreaseStock(2);

        assertThat(product.stock()).isEqualTo(3);
        assertThat(product.availabilityStatus()).isEqualTo(AvailabilityStatus.LOW_STOCK);
    }

    @Test
    void decreaseStockToZeroMakesProductUnavailable() {
        Product product = product(2, true);

        product.decreaseStock(2);

        assertThat(product.stock()).isZero();
        assertThat(product.availabilityStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
    }

    @Test
    void decreaseStockAboveStockThrowsAndKeepsStock() {
        Product product = product(2, true);

        assertThatThrownBy(() -> product.decreaseStock(3)).isInstanceOf(IllegalStateException.class);
        assertThat(product.stock()).isEqualTo(2);
    }

    @Test
    void decreaseStockByNonPositiveQuantityIsRejected() {
        Product product = product(2, true);

        assertThatThrownBy(() -> product.decreaseStock(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> product.decreaseStock(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeStockAndMissingImages() {
        assertThatThrownBy(() -> new Product(new ProductId(1), "Mug", "", Money.pln(1000), new CategoryId(1),
                -1, true, List.of(image(0)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Product(new ProductId(1), "Mug", "", Money.pln(1000), new CategoryId(1),
                1, true, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mainImageIsTheOneWithDisplayOrderZero() {
        Product product = new Product(new ProductId(1), "Mug", "", Money.pln(1000), new CategoryId(1), 1, true,
                List.of(image(1), image(0)));

        assertThat(product.images()).extracting(ProductImage::displayOrder).containsExactly(0, 1);
        assertThat(product.mainImage().displayOrder()).isZero();
    }

    private static Product product(int stock, boolean active) {
        return new Product(new ProductId(1), "Mug", "A mug", Money.pln(4999), new CategoryId(1), stock, active,
                List.of(image(0)));
    }

    private static ProductImage image(int displayOrder) {
        return new ProductImage("/images/mug-" + displayOrder + ".svg", "Mug", displayOrder);
    }
}

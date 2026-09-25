package com.project.custom.catalog.application;

import com.project.custom.catalog.CatalogCommandFacade;
import com.project.custom.catalog.StockDecreaseResult;
import com.project.custom.catalog.StockLineDto;
import com.project.custom.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R-16, FR-021: decreasing the stock of paid order lines, all or nothing, safe for concurrent buyers.
 */
class DecreaseStockIT extends IntegrationTest {

    @Autowired
    private CatalogCommandFacade catalogCommandFacade;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private long mug;
    private long plate;
    private long lastItem;

    @BeforeEach
    void createFixtureProducts() {
        long category = createFixtureCategory();
        mug = createFixtureProduct(category, "Kubek magazynowy", 12_900, 10, true);
        plate = createFixtureProduct(category, "Talerz magazynowy", 5_000, 2, true);
        lastItem = createFixtureProduct(category, "Ostatnia sztuka", 9_900, 1, true);
    }

    @Test
    void decreasesStockOfAllLines() {
        StockDecreaseResult result = catalogCommandFacade.decreaseStock(List.of(
                new StockLineDto(mug, 3), new StockLineDto(plate, 2)));

        assertThat(result).isEqualTo(StockDecreaseResult.DECREASED);
        assertThat(stock(mug)).isEqualTo(7);
        assertThat(stock(plate)).isZero();
    }

    @Test
    void changesNothingWhenAnyLineHasInsufficientStock() {
        StockDecreaseResult result = catalogCommandFacade.decreaseStock(List.of(
                new StockLineDto(mug, 3), new StockLineDto(plate, 3)));

        assertThat(result).isEqualTo(StockDecreaseResult.INSUFFICIENT_STOCK);
        assertThat(stock(mug)).isEqualTo(10);
        assertThat(stock(plate)).isEqualTo(2);
    }

    @Test
    void changesNothingWhenAProductDoesNotExist() {
        StockDecreaseResult result = catalogCommandFacade.decreaseStock(List.of(
                new StockLineDto(mug, 1), new StockLineDto(999_999_999L, 1)));

        assertThat(result).isEqualTo(StockDecreaseResult.INSUFFICIENT_STOCK);
        assertThat(stock(mug)).isEqualTo(10);
    }

    @Test
    void linesOfTheSameProductAreAddedUp() {
        StockDecreaseResult result = catalogCommandFacade.decreaseStock(List.of(
                new StockLineDto(plate, 1), new StockLineDto(plate, 2)));

        assertThat(result).isEqualTo(StockDecreaseResult.INSUFFICIENT_STOCK);
        assertThat(stock(plate)).isEqualTo(2);
    }

    @Test
    void onlyOneOfConcurrentBuyersGetsTheLastItem() throws Exception {
        CountDownLatch firstDecreased = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<StockDecreaseResult> first = executor.submit(() -> transactionTemplate.execute(status -> {
                StockDecreaseResult result = catalogCommandFacade.decreaseStock(List.of(new StockLineDto(lastItem, 1)));
                firstDecreased.countDown();
                sleep(500);
                return result;
            }));
            Future<StockDecreaseResult> second = executor.submit(() -> {
                firstDecreased.await(10, TimeUnit.SECONDS);
                return transactionTemplate.execute(status ->
                        catalogCommandFacade.decreaseStock(List.of(new StockLineDto(lastItem, 1))));
            });

            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactly(StockDecreaseResult.DECREASED, StockDecreaseResult.INSUFFICIENT_STOCK);
        } finally {
            executor.shutdownNow();
        }
        assertThat(stock(lastItem)).isZero();
    }

    private int stock(long productId) {
        return jdbcTemplate.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, productId);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}

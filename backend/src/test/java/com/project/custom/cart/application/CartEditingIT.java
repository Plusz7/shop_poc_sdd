package com.project.custom.cart.application;

import com.project.custom.cart.domain.Cart;
import com.project.custom.cart.domain.CartRepository;
import com.project.custom.shared.domain.GuestId;
import com.project.custom.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US3 (FR-008–FR-011, FR-014 for the cart part): changing quantities, removing lines, clearing the cart,
 * price change notices and loss of availability, against a real SQL Server.
 */
class CartEditingIT extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final String guest = UUID.randomUUID().toString();

    private long mug;
    private long lastFive;

    @BeforeEach
    void createCartWithTwoProducts() throws Exception {
        long category = createFixtureCategory();
        mug = createFixtureProduct(category, "Kubek edycyjny", 1_000, 50, true);
        lastFive = createFixtureProduct(category, "Pięć sztuk", 2_500, 5, true);
        add(mug, 1);
        add(lastFive, 1);
    }

    @Test
    void changingTheQuantityRecalculatesTheLineAndTheCartTotals() throws Exception {
        changeQuantity(mug, "3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].quantity").value(3))
                .andExpect(jsonPath("$.lines[0].lineTotalMinor").value(3_000))
                .andExpect(jsonPath("$.itemCount").value(4))
                .andExpect(jsonPath("$.totalMinor").value(5_500))
                .andExpect(jsonPath("$.canPlaceOrder").value(true))
                .andExpect(jsonPath("$.messages", hasSize(0)));
    }

    @Test
    void quantityAboveTheStockIsCappedWithAMessage() throws Exception {
        changeQuantity(lastFive, "50")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[1].quantity").value(5))
                .andExpect(jsonPath("$.totalMinor").value(1_000 + 5 * 2_500))
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].code").value("QUANTITY_CAPPED"))
                .andExpect(jsonPath("$.messages[0].productId").value(lastFive))
                .andExpect(jsonPath("$.messages[0].text").isNotEmpty());
    }

    @Test
    void quantityZeroRemovesTheLine() throws Exception {
        changeQuantity(mug, "0")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].productId").value(lastFive))
                .andExpect(jsonPath("$.totalMinor").value(2_500));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "-1", "\"abc\"", "100", "null"})
    void invalidQuantityIsAValidationErrorAndTheCartIsUnchanged(String quantity) throws Exception {
        changeQuantity(mug, quantity)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"));

        cart().andExpect(jsonPath("$.lines[0].quantity").value(1));
    }

    @Test
    void changingALineThatIsNotInTheCartIsNotFound() throws Exception {
        changeQuantity(Long.MAX_VALUE, "1")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void changingTheQuantityWithoutACartIsNotFound() throws Exception {
        mockMvc.perform(put("/api/cart/lines/{productId}", mug)
                        .cookie(new Cookie("shop_guest", UUID.randomUUID().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void removingALineIsIdempotent() throws Exception {
        removeLine(mug)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.totalMinor").value(2_500));

        removeLine(mug)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(1)));
    }

    @Test
    void clearingEmptiesTheCart() throws Exception {
        mockMvc.perform(delete("/api/cart").cookie(guestCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(0)))
                .andExpect(jsonPath("$.itemCount").value(0))
                .andExpect(jsonPath("$.totalMinor").value(0))
                .andExpect(jsonPath("$.canPlaceOrder").value(false));

        cart().andExpect(jsonPath("$.lines", hasSize(0)));
    }

    @Test
    void priceChangeIsReportedUntilTheCustomerAcceptsIt() throws Exception {
        jdbcTemplate.update("UPDATE product SET price_minor = ? WHERE id = ?", 1_200, mug);

        cart()
                .andExpect(jsonPath("$.lines[0].unitPriceMinor").value(1_200))
                .andExpect(jsonPath("$.lines[0].priceChanged").value(true))
                .andExpect(jsonPath("$.lines[0].previousPriceMinor").value(1_000))
                .andExpect(jsonPath("$.lines[1].priceChanged").value(false))
                .andExpect(jsonPath("$.totalMinor").value(1_200 + 2_500))
                .andExpect(jsonPath("$.canPlaceOrder").value(true))
                .andExpect(jsonPath("$.messages[0].code").value("PRICE_CHANGED"))
                .andExpect(jsonPath("$.messages[0].productId").value(mug));

        mockMvc.perform(post("/api/cart/accept-prices").cookie(guestCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].priceChanged").value(false))
                .andExpect(jsonPath("$.lines[0].previousPriceMinor").value(nullValue()))
                .andExpect(jsonPath("$.messages", hasSize(0)));

        cart().andExpect(jsonPath("$.lines[0].priceChanged").value(false));
    }

    @Test
    void soldOutProductIsUnavailableExcludedFromTheTotalAndBlocksOrdering() throws Exception {
        jdbcTemplate.update("UPDATE product SET stock = 0 WHERE id = ?", lastFive);

        cart()
                .andExpect(jsonPath("$.lines[1].status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.totalMinor").value(1_000))
                .andExpect(jsonPath("$.canPlaceOrder").value(false))
                .andExpect(jsonPath("$.messages[0].code").value("PRODUCT_UNAVAILABLE"))
                .andExpect(jsonPath("$.messages[0].productId").value(lastFive));
    }

    @Test
    void deactivatedProductIsUnavailable() throws Exception {
        jdbcTemplate.update("UPDATE product SET active = 0 WHERE id = ?", mug);

        cart()
                .andExpect(jsonPath("$.lines[0].status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.canPlaceOrder").value(false));
    }

    @Test
    void stockBelowTheLineQuantityIsReportedAndBlocksOrdering() throws Exception {
        changeQuantity(lastFive, "4").andExpect(status().isOk());
        jdbcTemplate.update("UPDATE product SET stock = 2 WHERE id = ?", lastFive);

        cart()
                .andExpect(jsonPath("$.lines[1].quantityExceedsStock").value(true))
                .andExpect(jsonPath("$.lines[1].maxQuantity").value(2))
                .andExpect(jsonPath("$.canPlaceOrder").value(false))
                .andExpect(jsonPath("$.messages[0].code").value("QUANTITY_EXCEEDS_STOCK"));

        changeQuantity(lastFive, "2")
                .andExpect(jsonPath("$.lines[1].quantityExceedsStock").value(false))
                .andExpect(jsonPath("$.canPlaceOrder").value(true));
    }

    @Test
    void settingAQuantityOfAnUnavailableProductIsRejected() throws Exception {
        jdbcTemplate.update("UPDATE product SET stock = 0 WHERE id = ?", lastFive);

        changeQuantity(lastFive, "1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"));
        changeQuantity(lastFive, "0")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(1)));
    }

    /**
     * Two tabs of the same guest: the first tab loaded the cart, the second tab changed it in the meantime;
     * the first tab's write must be rejected instead of silently overwriting the second tab's change. The
     * HTTP mapping of the rejection to {@code 409 CONCURRENCY_CONFLICT} is covered by {@code ErrorHandlingIT}.
     */
    @Test
    void staleWriteFromAnotherTabIsRejected() {
        GuestId guestId = new GuestId(UUID.fromString(guest));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            Cart firstTab = cartRepository.findByGuest(guestId).orElseThrow();
            CompletableFuture.runAsync(() -> {
                try {
                    changeQuantity(mug, "4").andExpect(status().isOk());
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }).join();
            firstTab.changeQuantity(mug, 2, 50, Instant.now());
            cartRepository.save(firstTab);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        Integer quantity = jdbcTemplate.queryForObject(
                "SELECT quantity FROM cart_line WHERE product_id = ?", Integer.class, mug);
        assertThat(quantity).isEqualTo(4);
    }

    private Cookie guestCookie() {
        return new Cookie("shop_guest", guest);
    }

    private ResultActions cart() throws Exception {
        return mockMvc.perform(get("/api/cart").cookie(guestCookie())).andExpect(status().isOk());
    }

    private void add(long productId, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/lines")
                        .cookie(guestCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": %d, \"quantity\": %d}".formatted(productId, quantity)))
                .andExpect(status().isOk());
    }

    private ResultActions changeQuantity(long productId, String quantity) throws Exception {
        return mockMvc.perform(put("/api/cart/lines/{productId}", productId)
                .cookie(guestCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": %s}".formatted(quantity)));
    }

    private ResultActions removeLine(long productId) throws Exception {
        return mockMvc.perform(delete("/api/cart/lines/{productId}", productId).cookie(guestCookie()));
    }
}

package com.project.custom.cart.application;

import com.project.custom.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US2 (FR-006–FR-008, FR-010, FR-012, FR-013): adding products to the guest's cart, priced with current
 * catalog prices, against a real SQL Server.
 */
class CartServiceIT extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final String guest = UUID.randomUUID().toString();

    private long mug;
    private long lastThree;
    private long soldOut;
    private long inactive;

    @BeforeEach
    void createFixtureProducts() {
        long category = createFixtureCategory();
        mug = createFixtureProduct(category, "Kubek koszykowy", 12_900, 10, true);
        lastThree = createFixtureProduct(category, "Ostatnie sztuki", 5_000, 3, true);
        soldOut = createFixtureProduct(category, "Wyprzedany", 5_000, 0, true);
        inactive = createFixtureProduct(category, "Wycofany", 5_000, 10, false);
    }

    @Test
    void firstReadWithoutCookieReturnsEmptyCartAndIssuesGuestCookie() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("shop_guest=")))
                .andExpect(jsonPath("$.lines", hasSize(0)))
                .andExpect(jsonPath("$.itemCount").value(0))
                .andExpect(jsonPath("$.totalMinor").value(0))
                .andExpect(jsonPath("$.canPlaceOrder").value(false))
                .andExpect(jsonPath("$.messages", hasSize(0)));
    }

    @Test
    void addingUsesTheCatalogPriceAndIgnoresAPriceSentByTheClient() throws Exception {
        add(guest, "{\"productId\": %d, \"quantity\": 2, \"unitPriceMinor\": 1}".formatted(mug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].productId").value(mug))
                .andExpect(jsonPath("$.lines[0].name").value("Kubek koszykowy"))
                .andExpect(jsonPath("$.lines[0].imageUrl").value("/images/test-" + mug + ".svg"))
                .andExpect(jsonPath("$.lines[0].unitPriceMinor").value(12_900))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.lines[0].lineTotalMinor").value(25_800))
                .andExpect(jsonPath("$.lines[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.lines[0].maxQuantity").value(10))
                .andExpect(jsonPath("$.lines[0].priceChanged").value(false))
                .andExpect(jsonPath("$.lines[0].quantityExceedsStock").value(false))
                .andExpect(jsonPath("$.itemCount").value(2))
                .andExpect(jsonPath("$.totalMinor").value(25_800));

        Long priceWhenAdded = jdbcTemplate.queryForObject(
                "SELECT price_when_added_minor FROM cart_line WHERE product_id = ?", Long.class, mug);
        assertThat(priceWhenAdded).isEqualTo(12_900L);
    }

    @Test
    void addingTheSameProductAgainMergesTheLine() throws Exception {
        add(guest, mug, 2).andExpect(status().isOk());

        add(guest, mug, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].quantity").value(3))
                .andExpect(jsonPath("$.itemCount").value(3))
                .andExpect(jsonPath("$.totalMinor").value(38_700));
    }

    @Test
    void exceedingTheStockIsRejectedWithTheRemainingQuantityAndTheCartIsUnchanged() throws Exception {
        add(guest, lastThree, 2).andExpect(status().isOk());

        add(guest, lastThree, 2)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("QUANTITY_EXCEEDS_LIMIT"))
                .andExpect(jsonPath("$.maxQuantity").value(1))
                .andExpect(jsonPath("$.detail").isString());

        cart(guest).andExpect(jsonPath("$.lines[0].quantity").value(2));
    }

    @Test
    void soldOutOrInactiveProductCannotBeAdded() throws Exception {
        add(guest, soldOut, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"));
        add(guest, inactive, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"));

        cart(guest).andExpect(jsonPath("$.lines", hasSize(0)));
    }

    @Test
    void nonExistentProductIsNotFound() throws Exception {
        add(guest, Long.MAX_VALUE, 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        add(guest, 0, 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "100", "1.5", "\"abc\"", "null"})
    void invalidQuantityIsAValidationError(String quantity) throws Exception {
        add(guest, "{\"productId\": %d, \"quantity\": %s}".formatted(mug, quantity))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"));

        cart(guest).andExpect(jsonPath("$.lines", hasSize(0)));
    }

    @Test
    void missingProductIdIsAValidationError() throws Exception {
        add(guest, "{\"quantity\": 1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("productId"));
    }

    @Test
    void cartIsKeptForTheSameCookieAndSeparateForAnotherGuest() throws Exception {
        add(guest, mug, 2).andExpect(status().isOk());

        cart(guest)
                .andExpect(jsonPath("$.lines[0].productId").value(mug))
                .andExpect(jsonPath("$.itemCount").value(2));
        cart(UUID.randomUUID().toString())
                .andExpect(jsonPath("$.lines", hasSize(0)))
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    void addingRenewsTheGuestCookie() throws Exception {
        add(guest, mug, 1)
                .andExpect(header().string("Set-Cookie", containsString("shop_guest=" + guest)));
    }

    private ResultActions cart(String guestId) throws Exception {
        return mockMvc.perform(get("/api/cart").cookie(new Cookie("shop_guest", guestId)))
                .andExpect(status().isOk());
    }

    private ResultActions add(String guestId, long productId, int quantity) throws Exception {
        return add(guestId, "{\"productId\": %d, \"quantity\": %d}".formatted(productId, quantity));
    }

    private ResultActions add(String guestId, String body) throws Exception {
        return mockMvc.perform(post("/api/cart/lines")
                .cookie(new Cookie("shop_guest", guestId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}

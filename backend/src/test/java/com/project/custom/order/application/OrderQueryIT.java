package com.project.custom.order.application;

import com.project.custom.support.CheckoutIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US4-6, FR-023, R-15: the confirmation page shows an order only to the guest who placed it, and reading it
 * never changes anything (FR-019).
 */
class OrderQueryIT extends CheckoutIntegrationTest {

    private final String guest = UUID.randomUUID().toString();

    private String number;

    @BeforeEach
    void placeOrderAwaitingPayment() throws Exception {
        long category = createFixtureCategory();
        long mug = createFixtureProduct(category, "Kubek potwierdzeniowy", 12_900, 10, true);
        addToCart(guest, mug, 2);
        number = placeOrder(guest);
    }

    @Test
    void ownerSeesTheOrder() throws Exception {
        mockMvc.perform(get("/api/orders/" + number).cookie(guestCookie(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(number))
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].name").value("Kubek potwierdzeniowy"))
                .andExpect(jsonPath("$.lines[0].unitPriceMinor").value(12_900))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.lines[0].lineTotalMinor").value(25_800))
                .andExpect(jsonPath("$.totalMinor").value(25_800))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.paidAt").value(nullValue()));
    }

    @Test
    void anotherGuestGetsNotFound() throws Exception {
        mockMvc.perform(get("/api/orders/" + number).cookie(guestCookie(UUID.randomUUID().toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void requestWithoutCookieGetsNotFound() throws Exception {
        mockMvc.perform(get("/api/orders/" + number))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unknownOrInvalidNumberGetsNotFound() throws Exception {
        Cookie owner = guestCookie(guest);
        mockMvc.perform(get("/api/orders/ORD-0000000000").cookie(owner))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/orders/not-a-number").cookie(owner))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void readingDoesNotChangeTheOrderOrThePayment() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/orders/" + number + "?session_id=cs_test_x").cookie(guestCookie(guest)))
                    .andExpect(status().isOk());
        }

        assertThat(orderStatus(number)).isEqualTo("AWAITING_PAYMENT");
        assertThat(paymentStatusOf(number)).isEqualTo("OPEN");
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM orders", Long.class)).isZero();
    }
}

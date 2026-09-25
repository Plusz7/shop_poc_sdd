package com.project.custom.order.application;

import com.project.custom.support.CheckoutIntegrationTest;
import com.project.custom.support.MetricsAssert;
import com.project.custom.support.StripeStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US4 (FR-014–FR-018, R-13, R-14): placing an order and starting its payment, against a real SQL Server and a
 * WireMock Stripe.
 */
class PlaceOrderServiceIT extends CheckoutIntegrationTest {

    private final String guest = UUID.randomUUID().toString();

    private long mug;
    private long plate;

    @BeforeEach
    void fillCart() throws Exception {
        long category = createFixtureCategory();
        mug = createFixtureProduct(category, "Kubek zamówieniowy", 12_900, 10, true);
        plate = createFixtureProduct(category, "Talerz zamówieniowy", 5_000, 5, true);
        addToCart(guest, mug, 2);
        addToCart(guest, plate, 1);
    }

    @Test
    void validOrderAwaitsPaymentWithAnOpenSessionAndLeavesTheCartAlone() throws Exception {
        placeOrder(guest, orderRequest(cart(guest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(matchesPattern("^ORD-[0-9A-HJKMNP-TV-Z]{10}$")))
                .andExpect(jsonPath("$.paymentUrl").value(startsWith("https://checkout.stripe.com/")))
                .andExpect(header().string("Location", startsWith("/api/orders/ORD-")));

        Map<String, Object> order = jdbcTemplate.queryForMap("SELECT * FROM orders");
        assertThat(order).containsEntry("status", "AWAITING_PAYMENT")
                .containsEntry("total_minor", 30_800L)
                .containsEntry("email", "jan.kowalski@example.com")
                .containsEntry("postal_code", "80-001")
                .containsEntry("country", "PL");
        assertThat(jdbcTemplate.queryForList(
                "SELECT name, unit_price_minor, quantity FROM order_line ORDER BY line_no"))
                .containsExactly(
                        Map.of("name", "Kubek zamówieniowy", "unit_price_minor", 12_900L, "quantity", 2),
                        Map.of("name", "Talerz zamówieniowy", "unit_price_minor", 5_000L, "quantity", 1));
        Map<String, Object> payment = jdbcTemplate.queryForMap("SELECT * FROM payment");
        assertThat(payment).containsEntry("status", "OPEN").containsEntry("amount_minor", 30_800L);
        assertThat((String) payment.get("stripe_session_id")).startsWith("cs_test_");
        assertThat(cartItemCount(guest)).isEqualTo(3);
        assertThat(stock(mug)).isEqualTo(10);
    }

    @Test
    void stripeIsNeverCalledInsideADatabaseTransaction() throws Exception {
        placeOrder(guest);
        addToCart(guest, plate, 1);
        placeOrder(guest);

        assertThat(paymentGatewayProbe.transactionActiveOnCall()).hasSize(3).containsOnly(false);
    }

    @Test
    void priceChangeAfterTheSummaryRequiresConfirmationAgain() throws Exception {
        Map<String, Object> request = orderRequest(cart(guest));
        jdbcTemplate.update("UPDATE product SET price_minor = 13900 WHERE id = ?", mug);

        placeOrder(guest, request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUMMARY_OUTDATED"))
                .andExpect(jsonPath("$.cart.totalMinor").value(2 * 13_900 + 5_000))
                .andExpect(jsonPath("$.cart.lines[0].unitPriceMinor").value(13_900))
                .andExpect(jsonPath("$.cart.messages[*].code", hasItem("PRICE_CHANGED")));

        assertNoOrder();
    }

    @Test
    void differentQuantityRequiresConfirmationAgain() throws Exception {
        Map<String, Object> request = orderRequest(cart(guest));
        addToCart(guest, plate, 1);

        placeOrder(guest, request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUMMARY_OUTDATED"))
                .andExpect(jsonPath("$.cart.itemCount").value(4));

        assertNoOrder();
    }

    @Test
    void differentContentsRequireConfirmationAgain() throws Exception {
        Map<String, Object> request = new HashMap<>(orderRequest(cart(guest)));
        request.put("confirmedSummary", Map.of(
                "lines", List.of(Map.of("productId", mug, "quantity", 2, "unitPriceMinor", 12_900)),
                "totalMinor", 25_800));

        placeOrder(guest, request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUMMARY_OUTDATED"));

        assertNoOrder();
    }

    @Test
    void productThatBecameUnavailableRequiresConfirmationAgain() throws Exception {
        Map<String, Object> request = orderRequest(cart(guest));
        jdbcTemplate.update("UPDATE product SET stock = 0 WHERE id = ?", plate);

        placeOrder(guest, request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUMMARY_OUTDATED"))
                .andExpect(jsonPath("$.cart.canPlaceOrder").value(false));

        assertNoOrder();
    }

    @Test
    void cartThatCannotBeOrderedIsRejected() throws Exception {
        jdbcTemplate.update("UPDATE product SET stock = 1 WHERE id = ?", mug);

        placeOrder(guest, orderRequest(cart(guest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_NOT_ORDERABLE"))
                .andExpect(jsonPath("$.cart.canPlaceOrder").value(false));

        assertNoOrder();
    }

    @Test
    void emptyCartIsRejected() throws Exception {
        String otherGuest = UUID.randomUUID().toString();
        Map<String, Object> request = orderRequest(cart(guest));

        placeOrder(otherGuest, request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_NOT_ORDERABLE"));

        assertNoOrder();
    }

    @Test
    void invalidFieldsAreReportedPerField() throws Exception {
        Map<String, Object> request = new HashMap<>(orderRequest(cart(guest)));
        request.put("postalCode", "12345");
        request.put("email", "");

        placeOrder(guest, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("postalCode")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[?(@.field == 'postalCode')].message",
                        containsInAnyOrder("Kod pocztowy musi mieć format NN-NNN.")));

        assertNoOrder();
    }

    @Test
    void countrySentByTheClientIsIgnored() throws Exception {
        Map<String, Object> request = new HashMap<>(orderRequest(cart(guest)));
        request.put("country", "DE");

        placeOrder(guest, request).andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject("SELECT country FROM orders", String.class)).isEqualTo("PL");
    }

    @Test
    void stripeUnavailableFailsTheOrderAndLeavesTheCartAlone() throws Exception {
        STRIPE.createSessionUnavailable();

        placeOrder(guest, orderRequest(cart(guest)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("Płatność jest chwilowo niedostępna, spróbuj ponownie za chwilę."));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders", String.class)).isEqualTo("PAYMENT_FAILED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM payment", String.class)).isEqualTo("FAILED");
        assertThat(cartItemCount(guest)).isEqualTo(3);
    }

    @Test
    void secondAttemptExpiresTheOpenSessionOfTheGuestsPreviousOrder() throws Exception {
        String first = placeOrder(guest);
        String firstSession = sessionIdOf(first);

        String second = placeOrder(guest);

        STRIPE.server().verify(postRequestedFor(urlEqualTo(StripeStub.SESSIONS + "/" + firstSession + "/expire")));
        assertThat(paymentStatusOf(first)).isEqualTo("EXPIRED");
        assertThat(orderStatus(first)).isEqualTo("PAYMENT_FAILED");
        assertThat(paymentStatusOf(second)).isEqualTo("OPEN");
        assertThat(orderStatus(second)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void otherGuestsSessionsAreNotExpired() throws Exception {
        String otherGuest = UUID.randomUUID().toString();
        addToCart(otherGuest, mug, 1);
        String othersOrder = placeOrder(otherGuest);

        placeOrder(guest);

        STRIPE.server().verify(0, postRequestedFor(urlMatching(".*/expire")));
        assertThat(paymentStatusOf(othersOrder)).isEqualTo("OPEN");
    }

    @Test
    void newCheckoutIsRefusedWhenThePreviousSessionHasBeenPaid() throws Exception {
        placeOrder(guest);
        STRIPE.sessionsAlreadyPaid();

        placeOrder(guest, orderRequest(cart(guest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_PAID"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
    }

    @Test
    void placedOrderIsCounted() throws Exception {
        MetricsAssert.Delta delta = metrics().delta(() -> placeOrder(guest));

        assertThat(delta.of("shop.orders.placed")).isEqualTo(1);
        assertThat(delta.of("shop.orders.completed", "status", "PAYMENT_FAILED")).isZero();
    }

    @Test
    void changedPriceIsCountedAsAPriceMismatch() throws Exception {
        Map<String, Object> request = orderRequest(cart(guest));
        jdbcTemplate.update("UPDATE product SET price_minor = 13900 WHERE id = ?", mug);

        MetricsAssert.Delta delta = metrics().delta(() -> placeOrder(guest, request).andExpect(status().isConflict()));

        assertMismatches(delta, 1, 0, 0);
        assertThat(delta.of("shop.orders.placed")).isZero();
    }

    @Test
    void unavailableProductIsCountedAsAnAvailabilityMismatch() throws Exception {
        Map<String, Object> request = orderRequest(cart(guest));
        jdbcTemplate.update("UPDATE product SET stock = 0 WHERE id = ?", plate);

        MetricsAssert.Delta delta = metrics().delta(() -> placeOrder(guest, request).andExpect(status().isConflict()));

        assertMismatches(delta, 0, 1, 0);
        assertThat(delta.of("shop.orders.placed")).isZero();
    }

    @Test
    void differentProductsAreCountedAsAContentsMismatch() throws Exception {
        Map<String, Object> request = new HashMap<>(orderRequest(cart(guest)));
        request.put("confirmedSummary", Map.of(
                "lines", List.of(Map.of("productId", mug, "quantity", 2, "unitPriceMinor", 12_900)),
                "totalMinor", 25_800));

        MetricsAssert.Delta delta = metrics().delta(() -> placeOrder(guest, request).andExpect(status().isConflict()));

        assertMismatches(delta, 0, 0, 1);
    }

    @Test
    void severalDifferencesAreCountedOncePerKind() throws Exception {
        Map<String, Object> request = orderRequest(cart(guest));
        jdbcTemplate.update("UPDATE product SET price_minor = 13900 WHERE id = ?", mug);
        addToCart(guest, plate, 1);

        MetricsAssert.Delta delta = metrics().delta(() -> placeOrder(guest, request).andExpect(status().isConflict()));

        assertMismatches(delta, 1, 0, 1);
    }

    @Test
    void unavailableStripeCountsTheCreatedOrderAndItsPaymentFailure() throws Exception {
        STRIPE.createSessionUnavailable();

        MetricsAssert.Delta delta = metrics().delta(() -> placeOrder(guest, orderRequest(cart(guest)))
                .andExpect(status().isServiceUnavailable()));

        assertThat(delta.of("shop.orders.placed")).isEqualTo(1);
        assertThat(delta.of("shop.orders.completed", "status", "PAYMENT_FAILED")).isEqualTo(1);
        assertThat(delta.of("shop.stripe.calls", "operation", "create_session", "outcome", "error")).isEqualTo(3);
        assertThat(delta.of("shop.stripe.retries", "operation", "create_session")).isEqualTo(2);
    }

    @Test
    void refusedOrdersAreNotCounted() throws Exception {
        jdbcTemplate.update("UPDATE product SET stock = 1 WHERE id = ?", mug);
        Map<String, Object> invalid = new HashMap<>(orderRequest(cart(guest)));
        invalid.put("postalCode", "12345");

        MetricsAssert.Delta delta = metrics().delta(() -> {
            placeOrder(guest, orderRequest(cart(guest))).andExpect(status().isConflict());
            placeOrder(guest, invalid).andExpect(status().isBadRequest());
        });

        assertThat(delta.of("shop.orders.placed")).isZero();
        assertMismatches(delta, 0, 0, 0);
    }

    private static void assertMismatches(MetricsAssert.Delta delta, int price, int availability, int contents) {
        assertThat(delta.of("shop.orders.mismatches", "kind", "PRICE")).isEqualTo(price);
        assertThat(delta.of("shop.orders.mismatches", "kind", "AVAILABILITY")).isEqualTo(availability);
        assertThat(delta.of("shop.orders.mismatches", "kind", "CONTENTS")).isEqualTo(contents);
    }

    private void assertNoOrder() {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment", Integer.class)).isZero();
        STRIPE.server().verify(0, postRequestedFor(urlEqualTo(StripeStub.SESSIONS)));
    }
}

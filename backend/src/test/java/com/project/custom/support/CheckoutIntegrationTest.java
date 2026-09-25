package com.project.custom.support;

import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class of US4 integration tests: filling a guest's cart and placing an order through the REST API,
 * exactly as the frontend does.
 */
public abstract class CheckoutIntegrationTest extends IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper jsonMapper;

    protected static Cookie guestCookie(String guest) {
        return new Cookie("shop_guest", guest);
    }

    protected void addToCart(String guest, long productId, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/lines").cookie(guestCookie(guest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": %d, \"quantity\": %d}".formatted(productId, quantity)))
                .andExpect(status().isOk());
    }

    /** The guest's cart as {@code GET /api/cart} returns it. */
    protected JsonNode cart(String guest) throws Exception {
        String body = mockMvc.perform(get("/api/cart").cookie(guestCookie(guest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body);
    }

    /** A valid order request confirming the given cart summary. */
    protected Map<String, Object> orderRequest(JsonNode cart) {
        List<Map<String, Object>> lines = cart.path("lines").valueStream()
                .map(line -> Map.<String, Object>of(
                        "productId", line.path("productId").longValue(),
                        "quantity", line.path("quantity").intValue(),
                        "unitPriceMinor", line.path("unitPriceMinor").longValue()))
                .toList();
        return Map.of(
                "email", "jan.kowalski@example.com",
                "fullName", "Jan Kowalski",
                "streetAndNumber", "ul. Długa 5",
                "postalCode", "80-001",
                "city", "Gdańsk",
                "confirmedSummary", Map.of("lines", lines, "totalMinor", cart.path("totalMinor").longValue()));
    }

    protected ResultActions placeOrder(String guest, Map<String, Object> request) throws Exception {
        return mockMvc.perform(post("/api/orders").cookie(guestCookie(guest))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)));
    }

    /** Places an order for the guest's current cart and returns its number; the order must be accepted. */
    protected String placeOrder(String guest) throws Exception {
        String body = placeOrder(guest, orderRequest(cart(guest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body).path("number").stringValue();
    }

    protected String orderStatus(String number) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE number = ?", String.class, number);
    }

    protected String sessionIdOf(String number) {
        return jdbcTemplate.queryForObject("""
                SELECT p.stripe_session_id FROM payment p JOIN orders o ON o.id = p.order_id
                WHERE o.number = ?""", String.class, number);
    }

    protected String paymentStatusOf(String number) {
        return jdbcTemplate.queryForObject("""
                SELECT p.status FROM payment p JOIN orders o ON o.id = p.order_id WHERE o.number = ?""",
                String.class, number);
    }

    protected int stock(long productId) {
        return jdbcTemplate.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, productId);
    }

    protected int cartItemCount(String guest) throws Exception {
        return cart(guest).path("itemCount").intValue();
    }
}

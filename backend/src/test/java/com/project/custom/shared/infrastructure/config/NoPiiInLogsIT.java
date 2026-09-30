package com.project.custom.shared.infrastructure.config;

import com.project.custom.support.CheckoutIntegrationTest;
import com.project.custom.support.StripeWebhookSigner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.project.custom.support.StripeWebhookSigner.SESSION_COMPLETED;
import static com.project.custom.support.StripeWebhookSigner.sessionEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Logs carry neither personal data nor secrets (constitution, security; FR-033): while placing an order and
 * handling a webhook nothing writes the full email, the delivery address, a Stripe key or a webhook secret,
 * and every entry logged while serving an API request carries its {@code [requestId]}.
 */
@ExtendWith(OutputCaptureExtension.class)
class NoPiiInLogsIT extends CheckoutIntegrationTest {

    private static final String REQUEST_ID = "pii-it-" + UUID.randomUUID().toString().substring(0, 8);

    /** An application log entry: MockMvc's own request/response printout and the test framework are not. */
    private static final Pattern LOG_ENTRY = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\S+\\s+\\w+ \\[");
    private static final Pattern TEST_CLASS_ENTRY = Pattern.compile("c\\.p\\.c\\.\\S+IT\\s+:");

    /** An entry logged by the application on the test thread, i.e. while MockMvc served a request. */
    private static final Pattern REQUEST_ENTRY =
            Pattern.compile("^\\S+\\s+\\w+ \\[(?<requestId>[^\\]]*)] \\d+ --- \\[shop] \\[\\s*main] c\\.p\\.c\\.");

    @Test
    void placingAnOrderAndHandlingAWebhookLeaksNoPersonalDataOrSecrets(CapturedOutput output) throws Exception {
        String guest = UUID.randomUUID().toString();
        long category = createFixtureCategory();
        long mug = createFixtureProduct(category, "Kubek logowy", 12_900, 10, true);
        addToCart(guest, mug, 2);

        String number = placeOrder(guest);
        String paid = sessionEvent(SESSION_COMPLETED, sessionIdOf(number), 25_800).json();
        deliver(paid, StripeWebhookSigner.sign(paid)).andExpect(status().isOk());
        deliver(paid, StripeWebhookSigner.sign(paid, Instant.now(), "whsec_test_forged"))
                .andExpect(status().isBadRequest());

        String logs = String.join("\n", applicationEntries(output));
        assertThat(logs).contains(number);
        assertThat(logs)
                .doesNotContain("jan.kowalski@example.com", "Jan Kowalski", "Kowalski", "ul. Długa 5", "Długa",
                        "80-001", "Gdańsk")
                .doesNotContain("sk_test_", "whsec_", "Bearer ")
                .doesNotContainPattern("[A-Za-z0-9._%+-]{2,}@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    }

    @Test
    void everyEntryLoggedWhileServingARequestCarriesItsRequestId(CapturedOutput output) throws Exception {
        String guest = UUID.randomUUID().toString();
        long category = createFixtureCategory();
        long mug = createFixtureProduct(category, "Kubek logowy", 12_900, 10, true);
        addToCart(guest, mug, 1);
        String number = placeOrderWithRequestId(guest);
        String paid = sessionEvent(SESSION_COMPLETED, sessionIdOf(number), 12_900).json();
        deliver(paid, StripeWebhookSigner.sign(paid)).andExpect(status().isOk());

        List<String> requestEntries = applicationEntries(output).stream()
                .filter(line -> REQUEST_ENTRY.matcher(line).find())
                .toList();

        assertThat(requestEntries).as("application entries logged while serving requests").isNotEmpty();
        assertThat(requestEntries).allSatisfy(line -> {
            var matcher = REQUEST_ENTRY.matcher(line);
            assertThat(matcher.find()).isTrue();
            assertThat(matcher.group("requestId")).as(line).isNotBlank();
        });
        assertThat(requestEntries).anySatisfy(line -> assertThat(line).contains("[" + REQUEST_ID + "]"));
    }

    private static List<String> applicationEntries(CapturedOutput output) {
        return output.getAll().lines()
                .filter(line -> LOG_ENTRY.matcher(line).find())
                .filter(line -> !TEST_CLASS_ENTRY.matcher(line).find())
                .toList();
    }

    private String placeOrderWithRequestId(String guest) throws Exception {
        String body = mockMvc.perform(post("/api/orders").cookie(guestCookie(guest)).header("X-Request-Id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(orderRequest(cart(guest)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body).path("number").stringValue();
    }

    private org.springframework.test.web.servlet.ResultActions deliver(String payload, String signature)
            throws Exception {
        return mockMvc.perform(post("/api/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", signature).content(payload));
    }
}

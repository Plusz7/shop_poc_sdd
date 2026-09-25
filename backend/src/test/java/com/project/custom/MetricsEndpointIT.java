package com.project.custom;

import com.project.custom.support.CheckoutIntegrationTest;
import com.project.custom.support.StripeWebhookSigner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.project.custom.support.StripeWebhookSigner.SESSION_COMPLETED;
import static com.project.custom.support.StripeWebhookSigner.sessionEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The metrics endpoint after a full purchase path (research R-33, SC-012; contracts/metrics.md): no personal
 * data or identifiers in any series, the common {@code application} label, exactly the contract's
 * {@code shop_*} metrics, and no actuator on the API port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MetricsEndpointIT extends CheckoutIntegrationTest {

    private static final Path CONTRACT = Path.of("..", "specs", "001-shop-browse-cart-checkout", "contracts",
            "metrics.md");
    private static final Pattern CONTRACT_ROW = Pattern.compile("^\\| `shop\\.[^`]+`[^|]*\\| `(shop_[a-z_]+)");
    private static final Pattern SHOP_SERIES = Pattern.compile("^(shop_[a-z_]+?)(_bucket|_count|_sum|_max)?[{ ]",
            Pattern.MULTILINE);
    private static final String REQUEST_ID = "metrics-it-" + UUID.randomUUID().toString().substring(0, 8);

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int apiPort;

    @LocalManagementPort
    private int managementPort;

    @Test
    void scrapeAfterAFullPurchaseContainsOnlyContractMetricsWithoutPersonalData() throws Exception {
        String guest = UUID.randomUUID().toString();
        String number = purchaseWithDuplicateAndForgedWebhook(guest);

        String scrape = fetch(managementPort, "/actuator/prometheus").body();

        assertThat(scrape).contains("shop_orders_completed_total");
        assertNoPersonalDataOrIdentifiers(scrape, guest, number);
        assertEveryShopSeriesHasTheApplicationLabel(scrape);
        assertThat(shopMetricNames(scrape)).isEqualTo(contractMetricNames());
    }

    @Test
    void apiPortDoesNotExposeTheActuator() throws Exception {
        assertThat(fetch(apiPort, "/actuator/prometheus").statusCode()).isEqualTo(404);
        assertThat(fetch(apiPort, "/actuator/health").statusCode()).isEqualTo(404);
    }

    @Test
    void readinessIsUpWithoutDetails() throws Exception {
        HttpResponse<String> readiness = fetch(managementPort, "/actuator/health/readiness");

        assertThat(readiness.statusCode()).isEqualTo(200);
        assertThat(readiness.body()).isEqualTo("{\"status\":\"UP\"}");
    }

    private String purchaseWithDuplicateAndForgedWebhook(String guest) throws Exception {
        long category = createFixtureCategory();
        long mug = createFixtureProduct(category, "Kubek metryczny", 12_900, 10, true);
        mockMvc.perform(post("/api/cart/lines").cookie(guestCookie(guest)).header("X-Request-Id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": %d, \"quantity\": 2}".formatted(mug)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", REQUEST_ID));
        String number = placeOrder(guest);
        mockMvc.perform(get("/api/orders/" + number).cookie(guestCookie(guest))).andExpect(status().isOk());

        String paid = sessionEvent(SESSION_COMPLETED, sessionIdOf(number), 25_800).json();
        deliver(paid, StripeWebhookSigner.sign(paid)).andExpect(status().isOk());
        deliver(paid, StripeWebhookSigner.sign(paid)).andExpect(status().isOk());
        deliver(paid, StripeWebhookSigner.sign(paid, Instant.now(), "whsec_test_forged"))
                .andExpect(status().isBadRequest());
        assertThat(orderStatus(number)).isEqualTo("PAID");
        return number;
    }

    private org.springframework.test.web.servlet.ResultActions deliver(String payload, String signature)
            throws Exception {
        return mockMvc.perform(post("/api/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", signature).content(payload));
    }

    private static void assertNoPersonalDataOrIdentifiers(String scrape, String guest, String number) {
        assertThat(scrape)
                .doesNotContainPattern("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
                .doesNotContain("Jan Kowalski", "Kowalski", "Długa", "Gdańsk", "80-001", "Kubek metryczny")
                .doesNotContainPattern("ORD-[0-9A-Z]{10}")
                .doesNotContainPattern("\\b(cs_test_|pi_|evt_|sk_|whsec_)")
                .doesNotContain(guest, number, REQUEST_ID);
    }

    private static void assertEveryShopSeriesHasTheApplicationLabel(String scrape) {
        scrape.lines()
                .filter(line -> line.startsWith("shop_"))
                .forEach(line -> assertThat(line).as(line).contains("application=\"shop\""));
    }

    private static Set<String> shopMetricNames(String scrape) {
        Set<String> names = new TreeSet<>();
        Matcher series = SHOP_SERIES.matcher(scrape);
        while (series.find()) {
            names.add(series.group(1));
        }
        return names;
    }

    private static Set<String> contractMetricNames() throws IOException {
        Set<String> names = new TreeSet<>();
        for (String line : Files.readAllLines(CONTRACT, StandardCharsets.UTF_8)) {
            Matcher row = CONTRACT_ROW.matcher(line);
            if (row.find()) {
                names.add(row.group(1).replaceFirst("_$", ""));
            }
        }
        assertThat(names).as("metrics listed in " + CONTRACT).hasSize(14);
        return names;
    }

    private HttpResponse<String> fetch(int port, String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}

package com.project.custom.payment.infrastructure.stripe;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.project.custom.payment.domain.ExpiryResult;
import com.project.custom.payment.domain.PaymentId;
import com.project.custom.payment.domain.PaymentSession;
import com.project.custom.payment.domain.SessionRequest;
import com.project.custom.payment.domain.SessionResult;
import com.project.custom.shared.domain.Money;
import com.project.custom.shared.infrastructure.config.AppProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract of the outgoing Stripe calls (contracts/stripe-webhook.md §1) against {@code stripe-mock}, which
 * validates every request against Stripe's OpenAPI specification. WireMock proxies the calls to it and records
 * them, so the test can also assert the exact parameters sent.
 */
@Testcontainers
class StripePaymentGatewayContractTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    // v0.196.0+ rejects payment_method_types[] on every endpoint ("additional properties are not allowed").
    @Container
    private static final GenericContainer<?> STRIPE_MOCK = new GenericContainer<>("stripe/stripe-mock:v0.195.0")
            .withExposedPorts(12111)
            .waitingFor(Wait.forListeningPort());

    private static WireMockServer recorder;

    private final PaymentId paymentId = PaymentId.random();
    private final UUID orderId = UUID.randomUUID();
    private StripePaymentGateway gateway;

    @BeforeAll
    static void startRecorder() {
        recorder = new WireMockServer(wireMockConfig().dynamicPort());
        recorder.start();
    }

    @AfterAll
    static void stopRecorder() {
        recorder.stop();
    }

    @BeforeEach
    void createGateway() {
        recorder.resetAll();
        recorder.stubFor(any(anyUrl()).willReturn(aResponse().proxiedFrom(
                "http://" + STRIPE_MOCK.getHost() + ":" + STRIPE_MOCK.getMappedPort(12111))));
        StripeProperties properties = new StripeProperties("sk_test_123", "whsec_test_x", Duration.ofSeconds(5),
                Duration.ofSeconds(10), URI.create(recorder.baseUrl()));
        AppProperties appProperties = new AppProperties(URI.create("http://localhost:5173"),
                new AppProperties.Cookie(false, Duration.ofDays(30)));
        gateway = new StripePaymentGateway(StripeClientConfig.create(properties), duration -> {
        }, appProperties, Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
    }

    @Test
    void createsAHostedCardPaymentSessionInPln() {
        SessionResult result = gateway.createSession(request());

        assertThat(result).isInstanceOf(PaymentSession.class);
        PaymentSession session = (PaymentSession) result;
        assertThat(session.providerSessionId()).startsWith("cs_");
        assertThat(session.url()).isNotBlank();

        LoggedRequest sent = recorder.findAll(postRequestedFor(urlEqualTo("/v1/checkout/sessions"))).getFirst();
        assertThat(sent.getHeader("Idempotency-Key")).isEqualTo("checkout-" + paymentId);
        assertThat(form(sent)).containsAllEntriesOf(Map.ofEntries(
                Map.entry("mode", "payment"),
                Map.entry("payment_method_types[0]", "card"),
                Map.entry("customer_email", "jan@example.com"),
                Map.entry("client_reference_id", orderId.toString()),
                Map.entry("metadata[orderId]", orderId.toString()),
                Map.entry("metadata[paymentId]", paymentId.toString()),
                Map.entry("metadata[number]", "ORD-7K2Q9M4XTB"),
                Map.entry("expires_at", String.valueOf(NOW.plus(Duration.ofMinutes(30)).getEpochSecond())),
                Map.entry("success_url", "http://localhost:5173/orders/ORD-7K2Q9M4XTB?session_id={CHECKOUT_SESSION_ID}"),
                Map.entry("cancel_url", "http://localhost:5173/cart?payment=canceled"),
                Map.entry("line_items[0][price_data][currency]", "pln"),
                Map.entry("line_items[0][price_data][unit_amount]", "12900"),
                Map.entry("line_items[0][price_data][product_data][name]", "Kubek"),
                Map.entry("line_items[0][quantity]", "2"),
                Map.entry("line_items[1][price_data][currency]", "pln"),
                Map.entry("line_items[1][price_data][unit_amount]", "5000"),
                Map.entry("line_items[1][price_data][product_data][name]", "Talerz"),
                Map.entry("line_items[1][quantity]", "1")));
    }

    @Test
    void sumOfUnitAmountTimesQuantityMustEqualTheTotal() {
        SessionRequest inconsistent = new SessionRequest(paymentId, orderId, "ORD-7K2Q9M4XTB",
                List.of(new SessionRequest.Line("Kubek", Money.pln(12_900), 2)), "jan@example.com", Money.pln(30_800));

        assertThatThrownBy(() -> gateway.createSession(inconsistent)).isInstanceOf(IllegalArgumentException.class);
        assertThat(recorder.getAllServeEvents()).isEmpty();
    }

    @Test
    void expiresAnOpenSession() {
        assertThat(gateway.expire("cs_test_a1b2c3")).isEqualTo(ExpiryResult.EXPIRED);
        recorder.verify(postRequestedFor(urlEqualTo("/v1/checkout/sessions/cs_test_a1b2c3/expire")));
    }

    private SessionRequest request() {
        return new SessionRequest(paymentId, orderId, "ORD-7K2Q9M4XTB",
                List.of(new SessionRequest.Line("Kubek", Money.pln(12_900), 2),
                        new SessionRequest.Line("Talerz", Money.pln(5_000), 1)),
                "jan@example.com", Money.pln(30_800));
    }

    private static Map<String, String> form(LoggedRequest request) {
        Map<String, String> form = new LinkedHashMap<>();
        Arrays.stream(request.getBodyAsString().split("&")).map(pair -> pair.split("=", 2))
                .forEach(pair -> form.put(decode(pair[0]), pair.length > 1 ? decode(pair[1]) : ""));
        return form;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}

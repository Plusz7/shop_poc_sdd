package com.project.custom.payment.infrastructure.stripe;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.project.custom.payment.domain.PaymentId;
import com.project.custom.payment.domain.SessionRequest;
import com.project.custom.shared.domain.Money;
import com.project.custom.shared.infrastructure.config.AppProperties;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stripe call metrics (contracts/metrics.md §2, FR-028): every attempt of the retry loop is timed with its
 * outcome, every retry is counted.
 */
class StripePaymentGatewayMetricsTest {

    private static final String SESSIONS = "/v1/checkout/sessions";
    private static final String SESSION_JSON = """
            {"id":"cs_test_1","object":"checkout.session","status":"open",\
            "url":"https://checkout.stripe.com/c/pay/cs_test_1"}""";

    private static WireMockServer wireMock;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private StripePaymentGateway gateway;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void createGateway() {
        wireMock.resetAll();
        StripeProperties properties = new StripeProperties("sk_test_metrics_test", "whsec_test_x",
                Duration.ofMillis(500), Duration.ofMillis(300), URI.create(wireMock.baseUrl()));
        AppProperties appProperties = new AppProperties(URI.create("http://localhost:5173"),
                new AppProperties.Cookie(false, Duration.ofDays(30)));
        gateway = new StripePaymentGateway(StripeClientConfig.create(properties), duration -> {
        }, appProperties, Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC), registry);
    }

    @Test
    void everyCombinationIsRegisteredWithZeroAtStartup() {
        for (String operation : List.of("create_session", "expire_session")) {
            for (String outcome : List.of("success", "error", "timeout")) {
                assertThat(calls(operation, outcome).count()).isZero();
            }
            assertThat(retries(operation)).isZero();
        }
    }

    @Test
    void successIsOneAttemptWithoutRetries() {
        wireMock.stubFor(post(SESSIONS).willReturn(aResponse().withStatus(200).withBody(SESSION_JSON)));

        gateway.createSession(request());

        assertThat(calls("create_session", "success").count()).isEqualTo(1);
        assertThat(retries("create_session")).isZero();
    }

    @Test
    void serverErrorsAreThreeErrorAttemptsAndTwoRetries() {
        wireMock.stubFor(post(SESSIONS).willReturn(stripeError(503, "api_error")));

        gateway.createSession(request());

        assertThat(calls("create_session", "error").count()).isEqualTo(3);
        assertThat(calls("create_session", "success").count()).isZero();
        assertThat(retries("create_session")).isEqualTo(2);
    }

    @Test
    void readTimeoutIsATimeoutOutcome() {
        wireMock.stubFor(post(SESSIONS).willReturn(aResponse().withFixedDelay(1_500).withBody(SESSION_JSON)));

        gateway.createSession(request());

        assertThat(calls("create_session", "timeout").count()).isEqualTo(3);
        assertThat(retries("create_session")).isEqualTo(2);
    }

    @Test
    void invalidRequestIsOneErrorAttemptWithoutRetries() {
        wireMock.stubFor(post(SESSIONS).willReturn(stripeError(400, "invalid_request_error")));

        assertThatThrownBy(() -> gateway.createSession(request())).isInstanceOf(StripeConfigurationException.class);

        assertThat(calls("create_session", "error").count()).isEqualTo(1);
        assertThat(retries("create_session")).isZero();
    }

    @Test
    void expiryIsTheExpireSessionOperation() {
        wireMock.stubFor(post(SESSIONS + "/cs_test_1/expire").willReturn(aResponse().withStatus(200).withBody("""
                {"id":"cs_test_1","object":"checkout.session","status":"expired"}""")));

        gateway.expire("cs_test_1");

        assertThat(calls("expire_session", "success").count()).isEqualTo(1);
        assertThat(calls("create_session", "success").count()).isZero();
    }

    @Test
    void callTimerHasTheContractBuckets() {
        assertThat(Arrays.stream(calls("create_session", "success").takeSnapshot().histogramCounts())
                .map(bucket -> bucket.bucket(TimeUnit.MILLISECONDS)))
                .containsExactly(100.0, 300.0, 1_000.0, 3_000.0, 10_000.0);
    }

    private Timer calls(String operation, String outcome) {
        return registry.get("shop.stripe.calls").tag("operation", operation).tag("outcome", outcome).timer();
    }

    private double retries(String operation) {
        return registry.get("shop.stripe.retries").tag("operation", operation).counter().count();
    }

    private static SessionRequest request() {
        return new SessionRequest(PaymentId.random(), UUID.randomUUID(), "ORD-7K2Q9M4XTB",
                List.of(new SessionRequest.Line("Kubek", Money.pln(12_900), 1)), "jan@example.com",
                Money.pln(12_900));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder stripeError(int status,
                                                                                                String type) {
        return aResponse().withStatus(status).withBody("""
                {"error":{"type":"%s","message":"Simulated failure"}}""".formatted(type));
    }
}

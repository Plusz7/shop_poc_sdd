package com.project.custom.payment.infrastructure.stripe;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.project.custom.payment.domain.ExpiryResult;
import com.project.custom.payment.domain.GatewayUnavailable;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Retry policy of the Stripe adapter (R-13, contracts/stripe-webhook.md §1) against WireMock: which failures
 * are retried, how often and after which delays, always with the same idempotency key.
 */
@ExtendWith(OutputCaptureExtension.class)
class StripePaymentGatewayFailureTest {

    private static final String SECRET_KEY = "sk_test_failure_test_secret_value";
    private static final String SESSIONS = "/v1/checkout/sessions";
    private static final String SESSION_JSON = """
            {"id":"cs_test_1","object":"checkout.session","status":"open",\
            "url":"https://checkout.stripe.com/c/pay/cs_test_1"}""";

    private static WireMockServer wireMock;

    private final List<Duration> sleeps = new ArrayList<>();
    private final PaymentId paymentId = PaymentId.random();
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
        StripeProperties properties = new StripeProperties(SECRET_KEY, "whsec_test_x", Duration.ofMillis(500),
                Duration.ofMillis(300), URI.create(wireMock.baseUrl()));
        AppProperties appProperties = new AppProperties(URI.create("http://localhost:5173"),
                new AppProperties.Cookie(false, Duration.ofDays(30)));
        gateway = new StripePaymentGateway(StripeClientConfig.create(properties), sleeps::add, appProperties,
                Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC), new SimpleMeterRegistry());
    }

    @Test
    void readTimeoutIsRetriedTwiceThenReportedUnavailable() {
        wireMock.stubFor(post(SESSIONS).willReturn(aResponse().withFixedDelay(1_500).withBody(SESSION_JSON)));

        assertUnavailableAfterThreeAttempts();
    }

    @Test
    void droppedConnectionIsRetriedTwiceThenReportedUnavailable() {
        wireMock.stubFor(post(SESSIONS).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertUnavailableAfterThreeAttempts();
    }

    @Test
    void serverErrorIsRetriedTwiceThenReportedUnavailable() {
        wireMock.stubFor(post(SESSIONS).willReturn(stripeError(500, "api_error")));

        assertUnavailableAfterThreeAttempts();
    }

    @Test
    void rateLimitIsRetriedTwiceThenReportedUnavailable() {
        wireMock.stubFor(post(SESSIONS).willReturn(stripeError(429, "invalid_request_error")));

        assertUnavailableAfterThreeAttempts();
    }

    @Test
    void transientErrorFollowedBySuccessCreatesTheSession() {
        wireMock.stubFor(post(SESSIONS).inScenario("flaky").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(stripeError(503, "api_error")).willSetStateTo("recovered"));
        wireMock.stubFor(post(SESSIONS).inScenario("flaky").whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200).withBody(SESSION_JSON)));

        SessionResult result = gateway.createSession(request());

        assertThat(result).isEqualTo(new PaymentSession("cs_test_1", "https://checkout.stripe.com/c/pay/cs_test_1"));
        assertThat(sleeps).containsExactly(Duration.ofMillis(500));
        wireMock.verify(2, postRequestedFor(urlEqualTo(SESSIONS))
                .withHeader("Idempotency-Key", equalTo("checkout-" + paymentId)));
    }

    @Test
    void invalidRequestIsNotRetriedAndHidesTheKey(CapturedOutput output) {
        wireMock.stubFor(post(SESSIONS).willReturn(stripeError(400, "invalid_request_error")));

        assertThatThrownBy(() -> gateway.createSession(request()))
                .isInstanceOf(StripeConfigurationException.class)
                .message().doesNotContain(SECRET_KEY);

        wireMock.verify(1, postRequestedFor(urlEqualTo(SESSIONS)));
        assertThat(sleeps).isEmpty();
        assertThat(output.getAll()).doesNotContain(SECRET_KEY);
    }

    @Test
    void authenticationErrorIsNotRetriedAndHidesTheKey(CapturedOutput output) {
        wireMock.stubFor(post(SESSIONS).willReturn(aResponse().withStatus(401).withBody("""
                {"error":{"type":"invalid_request_error",\
                "message":"Invalid API Key provided: %s"}}""".formatted(SECRET_KEY))));

        assertThatThrownBy(() -> gateway.createSession(request()))
                .isInstanceOf(StripeConfigurationException.class)
                .message().doesNotContain(SECRET_KEY);

        wireMock.verify(1, postRequestedFor(urlEqualTo(SESSIONS)));
        assertThat(output.getAll()).doesNotContain(SECRET_KEY);
    }

    @Test
    void expiringAPaidSessionReportsAlreadyPaid() {
        wireMock.stubFor(post(SESSIONS + "/cs_test_1/expire").willReturn(stripeError(400, "invalid_request_error")));
        wireMock.stubFor(get(SESSIONS + "/cs_test_1").willReturn(aResponse().withStatus(200).withBody("""
                {"id":"cs_test_1","object":"checkout.session","status":"complete"}""")));

        assertThat(gateway.expire("cs_test_1")).isEqualTo(ExpiryResult.ALREADY_PAID);
    }

    @Test
    void expiringAnExpiredSessionReportsExpired() {
        wireMock.stubFor(post(SESSIONS + "/cs_test_1/expire").willReturn(stripeError(400, "invalid_request_error")));
        wireMock.stubFor(get(SESSIONS + "/cs_test_1").willReturn(aResponse().withStatus(200).withBody("""
                {"id":"cs_test_1","object":"checkout.session","status":"expired"}""")));

        assertThat(gateway.expire("cs_test_1")).isEqualTo(ExpiryResult.EXPIRED);
    }

    @Test
    void expiryIsReportedUnavailableAfterRetries() {
        wireMock.stubFor(post(SESSIONS + "/cs_test_1/expire").willReturn(stripeError(502, "api_error")));

        assertThat(gateway.expire("cs_test_1")).isEqualTo(ExpiryResult.UNAVAILABLE);
        wireMock.verify(3, postRequestedFor(urlEqualTo(SESSIONS + "/cs_test_1/expire")));
    }

    @Test
    void inconsistentLinesAreRejectedBeforeCallingStripe() {
        SessionRequest inconsistent = new SessionRequest(paymentId, UUID.randomUUID(), "ORD-7K2Q9M4XTB",
                List.of(new SessionRequest.Line("Kubek", Money.pln(1_000), 2)), "jan@example.com", Money.pln(1_999));

        assertThatThrownBy(() -> gateway.createSession(inconsistent)).isInstanceOf(IllegalArgumentException.class);
        wireMock.verify(0, postRequestedFor(urlEqualTo(SESSIONS)));
    }

    private void assertUnavailableAfterThreeAttempts() {
        SessionResult result = gateway.createSession(request());

        assertThat(result).isInstanceOf(GatewayUnavailable.class);
        assertThat(sleeps).containsExactly(Duration.ofMillis(500), Duration.ofSeconds(1));
        wireMock.verify(3, postRequestedFor(urlEqualTo(SESSIONS))
                .withHeader("Idempotency-Key", equalTo("checkout-" + paymentId)));
    }

    private SessionRequest request() {
        return new SessionRequest(paymentId, UUID.randomUUID(), "ORD-7K2Q9M4XTB",
                List.of(new SessionRequest.Line("Kubek", Money.pln(12_900), 2),
                        new SessionRequest.Line("Talerz", Money.pln(5_000), 1)),
                "jan@example.com", Money.pln(30_800));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder stripeError(int status,
                                                                                                String type) {
        return aResponse().withStatus(status).withBody("""
                {"error":{"type":"%s","message":"Simulated failure"}}""".formatted(type));
    }
}

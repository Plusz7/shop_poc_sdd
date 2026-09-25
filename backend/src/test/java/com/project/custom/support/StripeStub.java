package com.project.custom.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * The Stripe API as seen by integration tests: a WireMock server started once per test run. By default a
 * session is created with a unique id and expiring a session succeeds; tests override single calls.
 */
public final class StripeStub {

    public static final String SESSIONS = "/v1/checkout/sessions";

    private final WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort().globalTemplating(true));

    StripeStub() {
        server.start();
    }

    public String baseUrl() {
        return server.baseUrl();
    }

    public WireMockServer server() {
        return server;
    }

    /** Session creation succeeds with a unique session id; expiry succeeds. */
    public void reset() {
        server.resetAll();
        server.stubFor(post(SESSIONS).willReturn(aResponse().withStatus(200).withBody("""
                {"id":"cs_test_{{randomValue length=24 type='ALPHANUMERIC'}}","object":"checkout.session",\
                "status":"open","url":"https://checkout.stripe.com/c/pay/{{randomValue length=24 type='ALPHANUMERIC'}}"}""")));
        server.stubFor(post(urlMatching(SESSIONS + "/[^/]+/expire")).willReturn(aResponse().withStatus(200).withBody("""
                {"id":"{{request.pathSegments.[3]}}","object":"checkout.session","status":"expired"}""")));
    }

    /** Session creation fails with {@code 500} on every attempt. */
    public void createSessionUnavailable() {
        server.stubFor(post(SESSIONS).willReturn(error(500, "api_error")));
    }

    /** Expiring a session is refused because it has been paid. */
    public void sessionsAlreadyPaid() {
        server.stubFor(post(urlMatching(SESSIONS + "/[^/]+/expire")).willReturn(error(400, "invalid_request_error")));
        server.stubFor(get(urlPathMatching(SESSIONS + "/[^/]+")).willReturn(aResponse().withStatus(200).withBody("""
                {"id":"{{request.pathSegments.[3]}}","object":"checkout.session","status":"complete"}""")));
    }

    private static ResponseDefinitionBuilder error(int status, String type) {
        return aResponse().withStatus(status).withBody("""
                {"error":{"type":"%s","message":"Simulated failure"}}""".formatted(type));
    }
}

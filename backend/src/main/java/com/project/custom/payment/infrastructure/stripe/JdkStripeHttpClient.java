package com.project.custom.payment.infrastructure.stripe;

import com.stripe.exception.ApiConnectionException;
import com.stripe.net.HttpClient;
import com.stripe.net.StripeRequest;
import com.stripe.net.StripeResponse;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stripe SDK transport on {@link java.net.http.HttpClient}. The SDK's default {@code HttpURLConnection}
 * transport silently repeats a POST after a dropped connection ({@code sun.net.http.retryPost}), which would
 * hide attempts from {@link StripeRetryPolicy} and the {@code shop.stripe.*} metrics: here every call is
 * exactly one HTTP request.
 */
class JdkStripeHttpClient extends HttpClient {

    /** Headers set by {@link java.net.http.HttpClient} itself; it refuses them from the caller. */
    private static final Set<String> RESTRICTED_HEADERS = Set.of("connection", "content-length", "host",
            "expect", "upgrade");

    private final java.net.http.HttpClient httpClient;
    private final Duration readTimeout;

    JdkStripeHttpClient(Duration connectTimeout, Duration readTimeout) {
        this.httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                .build();
        this.readTimeout = readTimeout;
    }

    @Override
    public StripeResponse request(StripeRequest request) throws ApiConnectionException {
        try {
            HttpResponse<String> response = httpClient.send(toHttpRequest(request),
                    HttpResponse.BodyHandlers.ofString());
            return new StripeResponse(response.statusCode(),
                    com.stripe.net.HttpHeaders.of(response.headers().map()), response.body());
        } catch (IOException exception) {
            throw new ApiConnectionException("IOException during API request to Stripe: "
                    + exception.getClass().getSimpleName(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiConnectionException("Interrupted during API request to Stripe", exception);
        }
    }

    private HttpRequest toHttpRequest(StripeRequest request) throws ApiConnectionException {
        HttpRequest.Builder builder;
        try {
            builder = HttpRequest.newBuilder(request.url().toURI()).timeout(readTimeout);
        } catch (URISyntaxException exception) {
            throw new ApiConnectionException("Invalid Stripe request URL", exception);
        }
        for (Map.Entry<String, List<String>> header : request.headers().map().entrySet()) {
            if (!RESTRICTED_HEADERS.contains(header.getKey().toLowerCase())) {
                header.getValue().forEach(value -> builder.header(header.getKey(), value));
            }
        }
        if (request.content() == null) {
            builder.method(request.method().name(), HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", request.content().contentType());
            builder.method(request.method().name(),
                    HttpRequest.BodyPublishers.ofByteArray(request.content().byteArrayContent()));
        }
        return builder.build();
    }
}

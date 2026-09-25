package com.project.custom.payment.infrastructure.stripe;

import com.stripe.StripeClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Stripe SDK client (R-13): timeouts from {@link StripeProperties}, and no retries in the SDK, because
 * {@link StripeRetryPolicy} retries and counts every attempt.
 */
@Configuration(proxyBeanMethods = false)
class StripeClientConfig {

    @Bean
    StripeClient stripeClient(StripeProperties properties) {
        return create(properties);
    }

    @Bean
    Sleeper stripeRetrySleeper() {
        return Sleeper.THREAD;
    }

    static StripeClient create(StripeProperties properties) {
        return StripeClient.builder()
                .setApiKey(properties.secretKey())
                .setApiBase(stripBase(properties.apiBase().toString()))
                .setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()))
                .setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()))
                .setMaxNetworkRetries(0)
                .build();
    }

    private static String stripBase(String apiBase) {
        return apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
    }
}

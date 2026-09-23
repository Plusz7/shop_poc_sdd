package com.project.custom.payment.infrastructure.stripe;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Stripe configuration validated at startup (Principles I and II, research R-20).
 * <p>
 * Validation lives in the compact constructor on purpose: a Bean Validation error would print the
 * rejected value (the key) in the exception and in the failure analysis. Messages here name only the
 * property. There is no retry setting - retrying is the adapter's policy (R-13).
 */
@ConfigurationProperties("shop.stripe")
public record StripeProperties(
        String secretKey,
        String webhookSecret,
        Duration connectTimeout,
        Duration readTimeout,
        @DefaultValue("https://api.stripe.com") URI apiBase) {

    private static final Pattern TEST_SECRET_KEY = Pattern.compile("^sk_test_.+");
    private static final Pattern WEBHOOK_SECRET = Pattern.compile("^whsec_.+");

    public StripeProperties {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("Missing required configuration: shop.stripe.secret-key");
        }
        if (!TEST_SECRET_KEY.matcher(secretKey).matches()) {
            throw new IllegalArgumentException("shop.stripe.secret-key must be a test key (sk_test_…)");
        }
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalArgumentException("Missing required configuration: shop.stripe.webhook-secret");
        }
        if (!WEBHOOK_SECRET.matcher(webhookSecret).matches()) {
            throw new IllegalArgumentException("shop.stripe.webhook-secret must be a webhook signing secret (whsec_…)");
        }
        requirePositive(connectTimeout, "shop.stripe.connect-timeout");
        requirePositive(readTimeout, "shop.stripe.read-timeout");
        if (apiBase == null) {
            throw new IllegalArgumentException("Missing required configuration: shop.stripe.api-base");
        }
    }

    private static void requirePositive(Duration duration, String property) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(property + " must be a positive duration");
        }
    }

    @Override
    public String toString() {
        return "StripeProperties[secretKey=sk_test_***, webhookSecret=whsec_***, connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + ", apiBase=" + apiBase + "]";
    }
}

package com.project.custom.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Builds Stripe webhook events and signs them like Stripe does ({@code Stripe-Signature: t=…,v1=…},
 * HMAC-SHA256 of {@code "<t>.<payload>"}) with the test-only secret of the test profile. No Stripe account
 * secret is involved (Principle VI).
 */
public final class StripeWebhookSigner {

    /** The {@code shop.stripe.webhook-secret} of application-test.yaml. */
    public static final String TEST_SECRET = "whsec_test_c2hvcC1pbnRlZ3JhdGlvbi10ZXN0cw";

    public static final String SESSION_COMPLETED = "checkout.session.completed";
    public static final String ASYNC_PAYMENT_SUCCEEDED = "checkout.session.async_payment_succeeded";
    public static final String ASYNC_PAYMENT_FAILED = "checkout.session.async_payment_failed";
    public static final String SESSION_EXPIRED = "checkout.session.expired";

    private StripeWebhookSigner() {
    }

    /** {@code Stripe-Signature} header for the payload, signed now with the test secret. */
    public static String sign(String payload) {
        return sign(payload, Instant.now(), TEST_SECRET);
    }

    public static String sign(String payload, Instant timestamp, String secret) {
        long t = timestamp.getEpochSecond();
        return "t=" + t + ",v1=" + hmacSha256(secret, t + "." + payload);
    }

    /** A new, unique event id. */
    public static String newEventId() {
        return "evt_test_" + UUID.randomUUID().toString().replace("-", "");
    }

    /** A checkout session event; builder defaults: paid, PLN, test mode, a new event id. */
    public static SessionEvent sessionEvent(String type, String sessionId, long amountTotal) {
        return new SessionEvent(newEventId(), type, sessionId, amountTotal, "pln", "paid", false);
    }

    /** An event of a type the shop does not handle. */
    public static String otherEvent(String type) {
        return """
                {"id":"%s","object":"event","api_version":"2025-01-27.acacia","created":%d,"livemode":false,\
                "type":"%s","data":{"object":{"id":"cus_test_1","object":"customer"}}}"""
                .formatted(newEventId(), Instant.now().getEpochSecond(), type);
    }

    public record SessionEvent(String eventId, String type, String sessionId, long amountTotal, String currency,
                               String paymentStatus, boolean livemode) {

        public SessionEvent withEventId(String id) {
            return new SessionEvent(id, type, sessionId, amountTotal, currency, paymentStatus, livemode);
        }

        public SessionEvent withCurrency(String value) {
            return new SessionEvent(eventId, type, sessionId, amountTotal, value, paymentStatus, livemode);
        }

        public SessionEvent withPaymentStatus(String value) {
            return new SessionEvent(eventId, type, sessionId, amountTotal, currency, value, livemode);
        }

        public SessionEvent withLivemode(boolean value) {
            return new SessionEvent(eventId, type, sessionId, amountTotal, currency, paymentStatus, value);
        }

        public String json() {
            return """
                    {"id":"%s","object":"event","api_version":"2025-01-27.acacia","created":%d,"livemode":%s,\
                    "type":"%s","data":{"object":{"id":"%s","object":"checkout.session","amount_total":%d,\
                    "currency":"%s","payment_status":"%s","payment_intent":"pi_test_%s","status":"complete",\
                    "livemode":%s}}}"""
                    .formatted(eventId, Instant.now().getEpochSecond(), livemode, type, sessionId, amountTotal,
                            currency, paymentStatus, sessionId, livemode);
        }
    }

    private static String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

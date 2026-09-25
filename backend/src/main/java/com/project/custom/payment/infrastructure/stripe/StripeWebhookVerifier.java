package com.project.custom.payment.infrastructure.stripe;

import com.project.custom.payment.domain.InvalidEventSignature;
import com.project.custom.payment.domain.ProviderConfirmation;
import com.project.custom.payment.domain.ProviderEventVerifier;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;

/**
 * Verifies the {@code Stripe-Signature} header of a webhook call (research R-12) and maps the event to the
 * SDK-independent {@link ProviderConfirmation}. The event object is read from the raw JSON, so the mapping
 * does not depend on the API version the event was rendered with.
 */
@Component
class StripeWebhookVerifier implements ProviderEventVerifier {

    /** Maximum age of a signature, in seconds. */
    static final long TOLERANCE_SECONDS = 300;

    private static final String CHECKOUT_SESSION = "checkout.session";
    private static final String PAID = "paid";

    private final String webhookSecret;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    StripeWebhookVerifier(StripeProperties properties, JsonMapper jsonMapper, Clock clock) {
        this.webhookSecret = properties.webhookSecret();
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    public ProviderConfirmation verify(String payload, String signatureHeader) {
        if (payload == null || signatureHeader == null || signatureHeader.isBlank()) {
            throw new InvalidEventSignature("Missing webhook payload or signature");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret, TOLERANCE_SECONDS, clock);
        } catch (SignatureVerificationException invalid) {
            throw new InvalidEventSignature("Webhook signature verification failed");
        } catch (RuntimeException unreadable) {
            throw new InvalidEventSignature("Webhook payload is not a Stripe event: "
                    + unreadable.getClass().getSimpleName());
        }
        JsonNode object = dataObject(event);
        boolean isSession = CHECKOUT_SESSION.equals(text(object, "object"));
        return new ProviderConfirmation(
                event.getId(),
                event.getType(),
                isSession ? text(object, "id") : null,
                text(object, "payment_intent"),
                number(object, "amount_total"),
                text(object, "currency"),
                isSession && PAID.equals(text(object, "payment_status")),
                Boolean.TRUE.equals(event.getLivemode()),
                event.getCreated() == null ? null : Instant.ofEpochSecond(event.getCreated()));
    }

    private JsonNode dataObject(Event event) {
        String rawJson = event.getDataObjectDeserializer().getRawJson();
        if (rawJson == null) {
            return jsonMapper.createObjectNode();
        }
        try {
            return jsonMapper.readTree(rawJson);
        } catch (JacksonException unreadable) {
            throw new InvalidEventSignature("Webhook event object is not JSON");
        }
    }

    private static String text(JsonNode object, String member) {
        JsonNode value = object.path(member);
        return value.isString() ? value.stringValue() : null;
    }

    private static Long number(JsonNode object, String member) {
        JsonNode value = object.path(member);
        return value.isNumber() ? value.longValue() : null;
    }
}
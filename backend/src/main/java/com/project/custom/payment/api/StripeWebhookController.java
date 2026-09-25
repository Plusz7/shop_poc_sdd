package com.project.custom.payment.api;

import com.project.custom.payment.application.WebhookHandlingService;
import com.project.custom.payment.application.WebhookRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stripe webhook endpoint (contracts/stripe-webhook.md §2). Passes the raw body and the signature header
 * unchanged to {@link WebhookHandlingService}; answers {@code 200} only after the effects are committed, so
 * that Stripe retries the delivery after a technical error ({@code 500}).
 */
@RestController
class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final WebhookHandlingService webhookHandlingService;

    StripeWebhookController(WebhookHandlingService webhookHandlingService) {
        this.webhookHandlingService = webhookHandlingService;
    }

    @PostMapping("/api/payments/stripe/webhook")
    ResponseEntity<Void> receive(@RequestBody(required = false) String payload,
                                 @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        try {
            webhookHandlingService.handle(payload, signature);
            return ResponseEntity.ok().build();
        } catch (WebhookRejectedException rejected) {
            log.warn("Stripe webhook rejected: {}", rejected.outcome());
            return ResponseEntity.badRequest().build();
        }
    }
}

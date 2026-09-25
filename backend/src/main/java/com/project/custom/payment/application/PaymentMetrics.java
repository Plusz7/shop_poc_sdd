package com.project.custom.payment.application;

import java.time.Duration;

/**
 * Business metrics of payments (research R-27–R-29, contracts/stripe-webhook.md §2 "Metrics"). Calls made
 * inside the webhook transaction are recorded only after its commit; rejections immediately.
 */
public interface PaymentMetrics {

    /** A webhook call was handled with this outcome. */
    void webhook(WebhookOutcome outcome);

    /** The provider reported a payment outcome. */
    void payment(PaymentOutcome outcome);

    /** Time from the provider creating a status-changing event to the commit of its handling. */
    void confirmationDelay(Duration delay);
}

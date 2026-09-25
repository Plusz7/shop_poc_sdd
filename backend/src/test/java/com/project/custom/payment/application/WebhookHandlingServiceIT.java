package com.project.custom.payment.application;

import com.project.custom.support.CheckoutIntegrationTest;
import com.project.custom.support.MetricsAssert;
import com.project.custom.support.StripeWebhookSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.project.custom.support.StripeWebhookSigner.ASYNC_PAYMENT_FAILED;
import static com.project.custom.support.StripeWebhookSigner.ASYNC_PAYMENT_SUCCEEDED;
import static com.project.custom.support.StripeWebhookSigner.SESSION_COMPLETED;
import static com.project.custom.support.StripeWebhookSigner.SESSION_EXPIRED;
import static com.project.custom.support.StripeWebhookSigner.sessionEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US4 (FR-019–FR-022, SC-004, SC-005; contracts/stripe-webhook.md §2): the Stripe webhook is the only way an
 * order becomes paid; it is verified, deduplicated and applied in one transaction.
 */
class WebhookHandlingServiceIT extends CheckoutIntegrationTest {

    private static final long TOTAL = 2 * 12_900 + 5_000;

    @Autowired
    private WebhookHandlingService webhookHandlingService;

    private final String guest = UUID.randomUUID().toString();

    private long mug;
    private long plate;
    private String number;
    private String sessionId;

    @BeforeEach
    void placeOrderAwaitingPayment() throws Exception {
        long category = createFixtureCategory();
        mug = createFixtureProduct(category, "Kubek webhookowy", 12_900, 10, true);
        plate = createFixtureProduct(category, "Talerz webhookowy", 5_000, 5, true);
        addToCart(guest, mug, 2);
        addToCart(guest, plate, 1);
        number = placeOrder(guest);
        sessionId = sessionIdOf(number);
    }

    @Test
    void paidSessionMarksOrderPaidDecreasesStockClearsCartAndWritesOutboxEvent() throws Exception {
        deliver(sessionEvent(SESSION_COMPLETED, sessionId, TOTAL).json())
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));

        assertThat(paymentStatusOf(number)).isEqualTo("CONFIRMED");
        assertThat(orderStatus(number)).isEqualTo("PAID");
        assertThat(jdbcTemplate.queryForObject("SELECT paid_at FROM orders", Object.class)).isNotNull();
        assertThat(stock(mug)).isEqualTo(8);
        assertThat(stock(plate)).isEqualTo(4);
        assertThat(cartItemCount(guest)).isZero();
        List<String> payloads = jdbcTemplate.queryForList(
                "SELECT payload FROM outbox_event WHERE type = 'OrderPaid' AND aggregate_id = ?", String.class, number);
        assertThat(payloads).hasSize(1);
        assertThat(payloads.getFirst())
                .contains("\"number\":\"" + number + "\"", "Kubek webhookowy", "Jan Kowalski", "80-001")
                .doesNotContain("jan.kowalski@example.com");
    }

    @Test
    void invalidSignatureIsRejectedWithoutEffects() throws Exception {
        String payload = sessionEvent(SESSION_COMPLETED, sessionId, TOTAL).json();

        deliver(payload, StripeWebhookSigner.sign(payload, Instant.now(), "whsec_test_forged"))
                .andExpect(status().isBadRequest());

        assertUnpaid();
    }

    @Test
    void missingSignatureIsRejectedWithoutEffects() throws Exception {
        mockMvc.perform(post("/api/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON)
                        .content(sessionEvent(SESSION_COMPLETED, sessionId, TOTAL).json()))
                .andExpect(status().isBadRequest());

        assertUnpaid();
    }

    @Test
    void tamperedPayloadIsRejectedWithoutEffects() throws Exception {
        String payload = sessionEvent(SESSION_COMPLETED, sessionId, 1).json();
        String signature = StripeWebhookSigner.sign(payload);

        deliver(payload.replace("\"amount_total\":1", "\"amount_total\":" + TOTAL), signature)
                .andExpect(status().isBadRequest());

        assertUnpaid();
    }

    @Test
    void signatureOlderThanToleranceIsRejected() throws Exception {
        String payload = sessionEvent(SESSION_COMPLETED, sessionId, TOTAL).json();

        deliver(payload, StripeWebhookSigner.sign(payload, Instant.now().minusSeconds(301),
                StripeWebhookSigner.TEST_SECRET))
                .andExpect(status().isBadRequest());

        assertUnpaid();
    }

    @Test
    void liveModeEventIsRejected() throws Exception {
        deliver(sessionEvent(SESSION_COMPLETED, sessionId, TOTAL).withLivemode(true).json())
                .andExpect(status().isBadRequest());

        assertUnpaid();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processed_stripe_event", Integer.class)).isZero();
    }

    @Test
    void sameEventDeliveredTwiceIsProcessedOnce() throws Exception {
        String payload = sessionEvent(SESSION_COMPLETED, sessionId, TOTAL).json();

        deliver(payload).andExpect(status().isOk());
        deliver(payload).andExpect(status().isOk());

        assertThat(stock(mug)).isEqualTo(8);
        assertThat(outboxEvents()).isEqualTo(1);
    }

    @Test
    void sameEventDeliveredConcurrentlyIsProcessedOnce() throws Exception {
        String payload = sessionEvent(SESSION_COMPLETED, sessionId, TOTAL).json();
        Callable<WebhookOutcome> delivery = () -> webhookHandlingService.handle(payload,
                StripeWebhookSigner.sign(payload));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<WebhookOutcome> first = executor.submit(delivery);
            Future<WebhookOutcome> second = executor.submit(delivery);

            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(WebhookOutcome.PROCESSED, WebhookOutcome.DUPLICATE);
        } finally {
            executor.shutdownNow();
        }
        assertThat(stock(mug)).isEqualTo(8);
        assertThat(outboxEvents()).isEqualTo(1);
    }

    @Test
    void anotherEventForTheSamePaidSessionHasNoEffects() throws Exception {
        deliver(sessionEvent(SESSION_COMPLETED, sessionId, TOTAL).json()).andExpect(status().isOk());

        deliver(sessionEvent(ASYNC_PAYMENT_SUCCEEDED, sessionId, TOTAL).json()).andExpect(status().isOk());

        assertThat(orderStatus(number)).isEqualTo("PAID");
        assertThat(stock(mug)).isEqualTo(8);
        assertThat(outboxEvents()).isEqualTo(1);
    }

    @Test
    void amountMismatchSendsOrderToReviewWithoutTouchingStock() throws Exception {
        deliver(sessionEvent(SESSION_COMPLETED, sessionId, TOTAL - 100).json()).andExpect(status().isOk());

        assertThat(orderStatus(number)).isEqualTo("NEEDS_REVIEW");
        assertThat(reviewReason()).isEqualTo("AMOUNT_MISMATCH");
        assertThat(stock(mug)).isEqualTo(10);
        assertThat(outboxEvents()).isZero();
    }

    @Test
    void currencyMismatchSendsOrderToReview() throws Exception {
        deliver(sessionEvent(SESSION_COMPLETED, sessionId, TOTAL).withCurrency("eur").json())
                .andExpect(status().isOk());

        assertThat(orderStatus(number)).isEqualTo("NEEDS_REVIEW");
        assertThat(reviewReason()).isEqualTo("AMOUNT_MISMATCH");
        assertThat(stock(mug)).isEqualTo(10);
    }

    @Test
    void insufficientStockSendsOrderToReviewAndClearsTheCart() throws Exception {
        jdbcTemplate.update("UPDATE product SET stock = 1 WHERE id = ?", mug);

        deliver(sessionEvent(SESSION_COMPLETED, sessionId, TOTAL).json()).andExpect(status().isOk());

        assertThat(paymentStatusOf(number)).isEqualTo("CONFIRMED");
        assertThat(orderStatus(number)).isEqualTo("NEEDS_REVIEW");
        assertThat(reviewReason()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(stock(mug)).isEqualTo(1);
        assertThat(stock(plate)).isEqualTo(5);
        assertThat(cartItemCount(guest)).isZero();
        assertThat(outboxEvents()).isZero();
    }

    @Test
    void completedButNotYetPaidSessionChangesNothing() throws Exception {
        deliver(sessionEvent(SESSION_COMPLETED, sessionId, TOTAL).withPaymentStatus("unpaid").json())
                .andExpect(status().isOk());

        assertUnpaid();
    }

    @Test
    void asyncPaymentSucceededMarksOrderPaid() throws Exception {
        deliver(sessionEvent(ASYNC_PAYMENT_SUCCEEDED, sessionId, TOTAL).json()).andExpect(status().isOk());

        assertThat(orderStatus(number)).isEqualTo("PAID");
        assertThat(stock(mug)).isEqualTo(8);
    }

    @Test
    void expiredSessionFailsTheOrderAndLeavesTheCartAlone() throws Exception {
        deliver(sessionEvent(SESSION_EXPIRED, sessionId, TOTAL).withPaymentStatus("unpaid").json())
                .andExpect(status().isOk());

        assertThat(paymentStatusOf(number)).isEqualTo("EXPIRED");
        assertThat(orderStatus(number)).isEqualTo("PAYMENT_FAILED");
        assertThat(cartItemCount(guest)).isEqualTo(3);
        assertThat(stock(mug)).isEqualTo(10);
    }

    @Test
    void asyncPaymentFailedFailsTheOrderAndLeavesTheCartAlone() throws Exception {
        deliver(sessionEvent(ASYNC_PAYMENT_FAILED, sessionId, TOTAL).withPaymentStatus("unpaid").json())
                .andExpect(status().isOk());

        assertThat(paymentStatusOf(number)).isEqualTo("FAILED");
        assertThat(orderStatus(number)).isEqualTo("PAYMENT_FAILED");
        assertThat(cartItemCount(guest)).isEqualTo(3);
    }

    @Test
    void lateConfirmationAfterFailureSendsOrderToReview() throws Exception {
        deliver(sessionEvent(SESSION_EXPIRED, sessionId, TOTAL).withPaymentStatus("unpaid").json())
                .andExpect(status().isOk());

        deliver(sessionEvent(SESSION_COMPLETED, sessionId, TOTAL).json()).andExpect(status().isOk());

        assertThat(orderStatus(number)).isEqualTo("NEEDS_REVIEW");
        assertThat(reviewReason()).isEqualTo("CONFIRMED_AFTER_FAILURE");
        assertThat(stock(mug)).isEqualTo(10);
    }

    @Test
    void unknownSessionIsAcknowledged() throws Exception {
        deliver(sessionEvent(SESSION_COMPLETED, "cs_test_unknown", TOTAL).json()).andExpect(status().isOk());

        assertUnpaid();
    }

    @Test
    void otherEventTypesAreAcknowledgedAndIgnored() throws Exception {
        deliver(StripeWebhookSigner.otherEvent("customer.created")).andExpect(status().isOk());

        assertUnpaid();
    }

    // Metrics per contracts/stripe-webhook.md §2 "Metrics" (R-28, R-29, SC-010)

    @Test
    void paidSessionIsCountedAsProcessedSucceededAndPaid() throws Exception {
        MetricsAssert.Delta delta = metrics().delta(() ->
                deliver(sessionEvent(SESSION_COMPLETED, sessionId, TOTAL).json()).andExpect(status().isOk()));

        assertThat(delta.of("shop.payment.webhook", "outcome", "processed")).isEqualTo(1);
        assertThat(delta.of("shop.payments", "outcome", "succeeded")).isEqualTo(1);
        assertThat(delta.of("shop.orders.completed", "status", "PAID")).isEqualTo(1);
        assertThat(delta.of("shop.orders.paid.value")).isEqualTo(TOTAL / 100.0);
        assertThat(delta.of("shop.order.time.to.payment")).isEqualTo(1);
        assertThat(delta.of("shop.payment.confirmation.delay")).isEqualTo(1);
        assertThat(delta.totalSecondsOf("shop.payment.confirmation.delay")).isBetween(0.0, 30.0);
    }

    @Test
    void duplicateIsCountedOnlyAsDuplicate() throws Exception {
        String payload = sessionEvent(SESSION_COMPLETED, sessionId, TOTAL).json();
        deliver(payload).andExpect(status().isOk());

        MetricsAssert.Delta delta = metrics().delta(() -> deliver(payload).andExpect(status().isOk()));

        assertThat(delta.of("shop.payment.webhook", "outcome", "duplicate")).isEqualTo(1);
        assertThat(delta.of("shop.payment.webhook", "outcome", "processed")).isZero();
        assertThat(delta.of("shop.payments", "outcome", "succeeded")).isZero();
        assertThat(delta.of("shop.orders.completed", "status", "PAID")).isZero();
        assertThat(delta.of("shop.orders.paid.value")).isZero();
    }

    @Test
    void forgedSignatureIsCountedAsRejected() throws Exception {
        String payload = sessionEvent(SESSION_COMPLETED, sessionId, TOTAL).json();

        MetricsAssert.Delta delta = metrics().delta(() ->
                deliver(payload, StripeWebhookSigner.sign(payload, Instant.now(), "whsec_test_forged"))
                        .andExpect(status().isBadRequest()));

        assertThat(delta.of("shop.payment.webhook", "outcome", "rejected_signature")).isEqualTo(1);
        assertThat(delta.of("shop.payment.webhook", "outcome", "processed")).isZero();
    }

    @Test
    void liveModeEventIsCountedAsRejected() throws Exception {
        MetricsAssert.Delta delta = metrics().delta(() ->
                deliver(sessionEvent(SESSION_COMPLETED, sessionId, TOTAL).withLivemode(true).json())
                        .andExpect(status().isBadRequest()));

        assertThat(delta.of("shop.payment.webhook", "outcome", "rejected_livemode")).isEqualTo(1);
    }

    @Test
    void declinedCardIsCountedWithoutChangingPaymentOrOrder() throws Exception {
        MetricsAssert.Delta delta = metrics().delta(() ->
                deliver(StripeWebhookSigner.paymentIntentFailedEvent()).andExpect(status().isOk()));

        assertThat(delta.of("shop.payment.webhook", "outcome", "processed")).isEqualTo(1);
        assertThat(delta.of("shop.payments", "outcome", "declined")).isEqualTo(1);
        assertThat(delta.of("shop.orders.completed", "status", "PAYMENT_FAILED")).isZero();
        assertUnpaid();
    }

    @Test
    void asyncPaymentFailureIsCountedAsDeclinedAndFailedOrder() throws Exception {
        MetricsAssert.Delta delta = metrics().delta(() ->
                deliver(sessionEvent(ASYNC_PAYMENT_FAILED, sessionId, TOTAL).withPaymentStatus("unpaid").json())
                        .andExpect(status().isOk()));

        assertThat(delta.of("shop.payments", "outcome", "declined")).isEqualTo(1);
        assertThat(delta.of("shop.orders.completed", "status", "PAYMENT_FAILED")).isEqualTo(1);
    }

    @Test
    void expiredSessionIsCountedAsCanceled() throws Exception {
        MetricsAssert.Delta delta = metrics().delta(() ->
                deliver(sessionEvent(SESSION_EXPIRED, sessionId, TOTAL).withPaymentStatus("unpaid").json())
                        .andExpect(status().isOk()));

        assertThat(delta.of("shop.payments", "outcome", "canceled")).isEqualTo(1);
        assertThat(delta.of("shop.orders.completed", "status", "PAYMENT_FAILED")).isEqualTo(1);
    }

    @Test
    void unpaidUnknownAndOtherEventsAreCountedAsIgnored() throws Exception {
        MetricsAssert.Delta delta = metrics().delta(() -> {
            deliver(sessionEvent(SESSION_COMPLETED, sessionId, TOTAL).withPaymentStatus("unpaid").json())
                    .andExpect(status().isOk());
            deliver(sessionEvent(SESSION_COMPLETED, "cs_test_unknown", TOTAL).json()).andExpect(status().isOk());
            deliver(StripeWebhookSigner.otherEvent("customer.created")).andExpect(status().isOk());
        });

        assertThat(delta.of("shop.payment.webhook", "outcome", "ignored")).isEqualTo(3);
        assertThat(delta.of("shop.payment.webhook", "outcome", "processed")).isZero();
        assertThat(delta.of("shop.payments", "outcome", "succeeded")).isZero();
    }

    @Test
    void insufficientStockIsCountedAsNeedsReview() throws Exception {
        jdbcTemplate.update("UPDATE product SET stock = 1 WHERE id = ?", mug);

        MetricsAssert.Delta delta = metrics().delta(() ->
                deliver(sessionEvent(SESSION_COMPLETED, sessionId, TOTAL).json()).andExpect(status().isOk()));

        assertThat(delta.of("shop.orders.completed", "status", "NEEDS_REVIEW")).isEqualTo(1);
        assertThat(delta.of("shop.orders.completed", "status", "PAID")).isZero();
        assertThat(delta.of("shop.orders.paid.value")).isZero();
    }

    @Test
    void lateConfirmationCountsNeedsReviewAndKeepsTheEarlierFailure() throws Exception {
        deliver(sessionEvent(SESSION_EXPIRED, sessionId, TOTAL).withPaymentStatus("unpaid").json())
                .andExpect(status().isOk());

        MetricsAssert.Delta delta = metrics().delta(() ->
                deliver(sessionEvent(SESSION_COMPLETED, sessionId, TOTAL).json()).andExpect(status().isOk()));

        assertThat(delta.of("shop.orders.completed", "status", "NEEDS_REVIEW")).isEqualTo(1);
        assertThat(delta.of("shop.orders.completed", "status", "PAYMENT_FAILED")).isZero();
    }

    @Test
    void rolledBackWebhookIsNotCounted() throws Exception {
        MetricsAssert.Delta delta;
        try (AutoCloseable failing = failingWritesTo("orders")) {
            delta = metrics().delta(() -> deliver(sessionEvent(SESSION_COMPLETED, sessionId, TOTAL).json())
                    .andExpect(status().isInternalServerError()));
        }

        assertThat(delta.of("shop.payment.webhook", "outcome", "processed")).isZero();
        assertThat(delta.of("shop.payments", "outcome", "succeeded")).isZero();
        assertThat(delta.of("shop.orders.completed", "status", "PAID")).isZero();
        assertUnpaid();
    }

    private ResultActions deliver(String payload) throws Exception {
        return deliver(payload, StripeWebhookSigner.sign(payload));
    }

    private ResultActions deliver(String payload, String signature) throws Exception {
        return mockMvc.perform(post("/api/payments/stripe/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", signature)
                .content(payload));
    }

    private void assertUnpaid() throws Exception {
        assertThat(orderStatus(number)).isEqualTo("AWAITING_PAYMENT");
        assertThat(paymentStatusOf(number)).isEqualTo("OPEN");
        assertThat(stock(mug)).isEqualTo(10);
        assertThat(cartItemCount(guest)).isEqualTo(3);
        assertThat(outboxEvents()).isZero();
    }

    private int outboxEvents() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_event", Integer.class);
    }

    private String reviewReason() {
        return jdbcTemplate.queryForObject("SELECT review_reason FROM orders WHERE number = ?", String.class, number);
    }
}

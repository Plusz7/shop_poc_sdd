package com.project.custom.payment.application;

import com.project.custom.payment.PaymentConfirmedEvent;
import com.project.custom.payment.PaymentFailedEvent;
import com.project.custom.payment.domain.InvalidEventSignature;
import com.project.custom.payment.domain.Payment;
import com.project.custom.payment.domain.PaymentRepository;
import com.project.custom.payment.domain.ProcessedEventRepository;
import com.project.custom.payment.domain.ProviderConfirmation;
import com.project.custom.payment.domain.ProviderEventVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Handles Stripe webhook calls (research R-12, contracts/stripe-webhook.md §2): the signature and test mode
 * are checked before any transaction starts; then, in one transaction, the event id is registered for
 * deduplication, the payment changes state and the order context reacts to the published domain event.
 * A payment is marked paid only here (FR-019).
 */
@Service
public class WebhookHandlingService {

    static final String SESSION_COMPLETED = "checkout.session.completed";
    static final String ASYNC_PAYMENT_SUCCEEDED = "checkout.session.async_payment_succeeded";
    static final String ASYNC_PAYMENT_FAILED = "checkout.session.async_payment_failed";
    static final String SESSION_EXPIRED = "checkout.session.expired";
    static final String PAYMENT_INTENT_FAILED = "payment_intent.payment_failed";

    private static final Logger log = LoggerFactory.getLogger(WebhookHandlingService.class);
    private static final String EVENT_ID_MDC_KEY = "stripeEventId";

    private final ProviderEventVerifier eventVerifier;
    private final ProcessedEventRepository processedEventRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentMetrics paymentMetrics;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    WebhookHandlingService(ProviderEventVerifier eventVerifier, ProcessedEventRepository processedEventRepository,
                           PaymentRepository paymentRepository, ApplicationEventPublisher eventPublisher,
                           PaymentMetrics paymentMetrics, TransactionTemplate transactionTemplate, Clock clock) {
        this.eventVerifier = eventVerifier;
        this.processedEventRepository = processedEventRepository;
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
        this.paymentMetrics = paymentMetrics;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /**
     * @param payload         the raw request body
     * @param signatureHeader the {@code Stripe-Signature} header, {@code null} when missing
     * @return how the event was handled; effects are committed when this returns
     * @throws WebhookRejectedException when the signature is invalid or the event comes from live mode
     */
    public WebhookOutcome handle(String payload, String signatureHeader) {
        ProviderConfirmation event;
        try {
            event = eventVerifier.verify(payload, signatureHeader);
        } catch (InvalidEventSignature invalid) {
            paymentMetrics.webhook(WebhookOutcome.REJECTED_SIGNATURE);
            throw new WebhookRejectedException(WebhookOutcome.REJECTED_SIGNATURE, invalid.getMessage());
        }
        if (event.livemode()) {
            paymentMetrics.webhook(WebhookOutcome.REJECTED_LIVEMODE);
            throw new WebhookRejectedException(WebhookOutcome.REJECTED_LIVEMODE, "Live mode events are not accepted");
        }
        MDC.put(EVENT_ID_MDC_KEY, event.eventId());
        try {
            WebhookOutcome outcome = transactionTemplate.execute(status -> process(event));
            log.info("Stripe event {} handled: {}", event.type(), outcome);
            return outcome;
        } finally {
            MDC.remove(EVENT_ID_MDC_KEY);
        }
    }

    /** Runs in the webhook transaction; the reported metrics are recorded only after its commit. */
    private WebhookOutcome process(ProviderConfirmation event) {
        Instant now = clock.instant();
        if (!processedEventRepository.register(event.eventId(), event.type(), now)) {
            paymentMetrics.webhook(WebhookOutcome.DUPLICATE);
            return WebhookOutcome.DUPLICATE;
        }
        WebhookOutcome outcome = switch (event.type()) {
            case SESSION_COMPLETED -> event.paid() ? confirm(event, now) : WebhookOutcome.IGNORED;
            case ASYNC_PAYMENT_SUCCEEDED -> confirm(event, now);
            case ASYNC_PAYMENT_FAILED -> end(event, Payment::fail, PaymentFailedEvent.Reason.FAILED);
            case SESSION_EXPIRED -> end(event, Payment::expire, PaymentFailedEvent.Reason.EXPIRED);
            // The session stays open and the customer can try another card; reported in metrics only (R-28).
            case PAYMENT_INTENT_FAILED -> WebhookOutcome.PROCESSED;
            default -> WebhookOutcome.IGNORED;
        };
        paymentMetrics.webhook(outcome);
        if (outcome == WebhookOutcome.PROCESSED) {
            paymentOutcome(event.type()).ifPresent(payment -> {
                paymentMetrics.payment(payment);
                if (event.providerCreatedAt() != null) {
                    paymentMetrics.confirmationDelay(Duration.between(event.providerCreatedAt(), now));
                }
            });
        }
        return outcome;
    }

    /** Mapping of handled events to payment outcomes (R-28). */
    private static Optional<PaymentOutcome> paymentOutcome(String type) {
        return switch (type) {
            case SESSION_COMPLETED, ASYNC_PAYMENT_SUCCEEDED -> Optional.of(PaymentOutcome.SUCCEEDED);
            case ASYNC_PAYMENT_FAILED, PAYMENT_INTENT_FAILED -> Optional.of(PaymentOutcome.DECLINED);
            case SESSION_EXPIRED -> Optional.of(PaymentOutcome.CANCELED);
            default -> Optional.empty();
        };
    }

    private WebhookOutcome confirm(ProviderConfirmation event, Instant now) {
        Optional<Payment> found = payment(event);
        if (found.isEmpty()) {
            return WebhookOutcome.IGNORED;
        }
        Payment payment = found.get();
        if (payment.confirm(event.paymentIntentId(), now)) {
            paymentRepository.save(payment);
            long amount = event.amountMinor() == null ? 0 : event.amountMinor();
            eventPublisher.publishEvent(new PaymentConfirmedEvent(payment.orderId(), amount, event.currency(), now));
        }
        return WebhookOutcome.PROCESSED;
    }

    private WebhookOutcome end(ProviderConfirmation event, Predicate<Payment> transition,
                               PaymentFailedEvent.Reason reason) {
        Optional<Payment> found = payment(event);
        if (found.isEmpty()) {
            return WebhookOutcome.IGNORED;
        }
        Payment payment = found.get();
        if (transition.test(payment)) {
            paymentRepository.save(payment);
            eventPublisher.publishEvent(new PaymentFailedEvent(payment.orderId(), reason));
        }
        return WebhookOutcome.PROCESSED;
    }

    private Optional<Payment> payment(ProviderConfirmation event) {
        Optional<Payment> payment = event.sessionId() == null
                ? Optional.empty()
                : paymentRepository.findByProviderSessionId(event.sessionId());
        if (payment.isEmpty()) {
            log.warn("Stripe event {} refers to an unknown checkout session", event.type());
        }
        return payment;
    }
}

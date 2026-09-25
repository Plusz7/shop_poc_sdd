package com.project.custom.payment.domain;

import com.project.custom.shared.domain.GuestId;
import com.project.custom.shared.domain.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One attempt to pay for an order through the payment provider (data-model.md). Transitions:
 * {@code CREATED → OPEN | FAILED}, {@code OPEN → CONFIRMED | EXPIRED | FAILED}; {@code CONFIRMED} is final.
 * A confirmation arriving after an expiry or failure is still recorded, so that the order can be reviewed.
 */
public class Payment {

    private final PaymentId id;
    private final UUID orderId;
    private final GuestId guestId;
    private final Money amount;
    private final Instant createdAt;
    private final long version;
    private PaymentStatus status;
    private String providerSessionId;
    private String paymentUrl;
    private String providerPaymentId;
    private Instant confirmedAt;

    private Payment(PaymentId id, UUID orderId, GuestId guestId, Money amount, PaymentStatus status,
                    String providerSessionId, String paymentUrl, String providerPaymentId, Instant createdAt,
                    Instant confirmedAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.guestId = Objects.requireNonNull(guestId, "guestId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.providerSessionId = providerSessionId;
        this.paymentUrl = paymentUrl;
        this.providerPaymentId = providerPaymentId;
        this.confirmedAt = confirmedAt;
        this.version = version;
    }

    public static Payment create(PaymentId id, UUID orderId, GuestId guestId, Money amount, Instant now) {
        return new Payment(id, orderId, guestId, amount, PaymentStatus.CREATED, null, null, null, now, null, 0);
    }

    public static Payment restore(PaymentId id, UUID orderId, GuestId guestId, Money amount, PaymentStatus status,
                                  String providerSessionId, String paymentUrl, String providerPaymentId,
                                  Instant createdAt, Instant confirmedAt, long version) {
        return new Payment(id, orderId, guestId, amount, status, providerSessionId, paymentUrl, providerPaymentId,
                createdAt, confirmedAt, version);
    }

    /** The provider session was created; the customer can pay at {@code paymentUrl}. */
    public void open(String sessionId, String url) {
        Objects.requireNonNull(url, "url");
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Provider session id is required");
        }
        if (status != PaymentStatus.CREATED) {
            throw new IllegalStateException("Only a created payment can be opened, not " + status);
        }
        providerSessionId = sessionId;
        paymentUrl = url;
        status = PaymentStatus.OPEN;
    }

    /**
     * The provider confirmed the payment.
     *
     * @return {@code false} when it had already been confirmed (a repeated confirmation, FR-020)
     */
    public boolean confirm(String providerPaymentId, Instant now) {
        if (status == PaymentStatus.CONFIRMED) {
            return false;
        }
        if (status == PaymentStatus.CREATED) {
            throw new IllegalStateException("A payment without a provider session cannot be confirmed");
        }
        this.providerPaymentId = providerPaymentId;
        confirmedAt = now;
        status = PaymentStatus.CONFIRMED;
        return true;
    }

    /**
     * The provider session expired unpaid.
     *
     * @return {@code false} when the payment had already ended (confirmed, expired or failed)
     */
    public boolean expire() {
        if (status == PaymentStatus.CREATED) {
            throw new IllegalStateException("A payment without a provider session cannot expire");
        }
        return end(PaymentStatus.EXPIRED);
    }

    /**
     * The payment failed, or its session could not be created.
     *
     * @return {@code false} when the payment had already ended (confirmed, expired or failed)
     */
    public boolean fail() {
        if (status == PaymentStatus.CREATED) {
            status = PaymentStatus.FAILED;
            return true;
        }
        return end(PaymentStatus.FAILED);
    }

    private boolean end(PaymentStatus target) {
        if (status != PaymentStatus.OPEN) {
            return false;
        }
        status = target;
        return true;
    }

    public PaymentId id() {
        return id;
    }

    public UUID orderId() {
        return orderId;
    }

    public GuestId guestId() {
        return guestId;
    }

    public Money amount() {
        return amount;
    }

    public PaymentStatus status() {
        return status;
    }

    public Optional<String> providerSessionId() {
        return Optional.ofNullable(providerSessionId);
    }

    public Optional<String> paymentUrl() {
        return Optional.ofNullable(paymentUrl);
    }

    public Optional<String> providerPaymentId() {
        return Optional.ofNullable(providerPaymentId);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<Instant> confirmedAt() {
        return Optional.ofNullable(confirmedAt);
    }

    /** Optimistic locking version of the loaded state. */
    public long version() {
        return version;
    }
}

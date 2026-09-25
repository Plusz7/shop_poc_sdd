package com.project.custom.payment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Nationalized;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment")
class PaymentJpaEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "guest_id", nullable = false)
    private UUID guestId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "stripe_session_id")
    private String stripeSessionId;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Nationalized
    @Column(name = "payment_url", length = 1000)
    private String paymentUrl;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    /** {@code null} until first stored, so that Spring Data persists (not merges) a new payment. */
    @Version
    @Column(nullable = false)
    private Long version;

    protected PaymentJpaEntity() {
    }

    PaymentJpaEntity(UUID id, UUID orderId, UUID guestId, long amountMinor, OffsetDateTime createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.guestId = guestId;
        this.amountMinor = amountMinor;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getOrderId() {
        return orderId;
    }

    UUID getGuestId() {
        return guestId;
    }

    long getAmountMinor() {
        return amountMinor;
    }

    String getStripeSessionId() {
        return stripeSessionId;
    }

    String getStripePaymentIntentId() {
        return stripePaymentIntentId;
    }

    String getPaymentUrl() {
        return paymentUrl;
    }

    String getStatus() {
        return status;
    }

    OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }

    long getVersion() {
        return version == null ? 0 : version;
    }

    void update(String status, String stripeSessionId, String paymentUrl, String stripePaymentIntentId,
                OffsetDateTime confirmedAt) {
        this.status = status;
        this.stripeSessionId = stripeSessionId;
        this.paymentUrl = paymentUrl;
        this.stripePaymentIntentId = stripePaymentIntentId;
        this.confirmedAt = confirmedAt;
    }
}

package com.project.custom.payment.infrastructure.persistence;

import com.project.custom.payment.domain.Payment;
import com.project.custom.payment.domain.PaymentId;
import com.project.custom.payment.domain.PaymentRepository;
import com.project.custom.payment.domain.PaymentStatus;
import com.project.custom.shared.domain.GuestId;
import com.project.custom.shared.domain.Money;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
class PaymentRepositoryAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    PaymentRepositoryAdapter(PaymentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Payment> findById(PaymentId id) {
        return jpaRepository.findById(id.value()).map(PaymentRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Payment> findByProviderSessionId(String providerSessionId) {
        return jpaRepository.findByStripeSessionId(providerSessionId).map(PaymentRepositoryAdapter::toDomain);
    }

    @Override
    public List<Payment> findOpenByGuest(GuestId guestId) {
        return jpaRepository.findByGuestIdAndStatusOrderByCreatedAtAsc(guestId.value(), PaymentStatus.OPEN.name())
                .stream()
                .map(PaymentRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public void save(Payment payment) {
        PaymentJpaEntity entity = jpaRepository.findById(payment.id().value())
                .orElseGet(() -> new PaymentJpaEntity(payment.id().value(), payment.orderId(),
                        payment.guestId().value(), payment.amount().minor(), toOffset(payment.createdAt())));
        if (entity.getVersion() != payment.version()) {
            throw new ObjectOptimisticLockingFailureException(PaymentJpaEntity.class, payment.id().value());
        }
        entity.update(payment.status().name(), payment.providerSessionId().orElse(null),
                payment.paymentUrl().orElse(null), payment.providerPaymentId().orElse(null),
                payment.confirmedAt().map(PaymentRepositoryAdapter::toOffset).orElse(null));
        jpaRepository.save(entity);
    }

    private static Payment toDomain(PaymentJpaEntity entity) {
        return Payment.restore(
                new PaymentId(entity.getId()),
                entity.getOrderId(),
                new GuestId(entity.getGuestId()),
                Money.pln(entity.getAmountMinor()),
                PaymentStatus.valueOf(entity.getStatus()),
                entity.getStripeSessionId(),
                entity.getPaymentUrl(),
                entity.getStripePaymentIntentId(),
                entity.getCreatedAt().toInstant(),
                entity.getConfirmedAt() == null ? null : entity.getConfirmedAt().toInstant(),
                entity.getVersion());
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}

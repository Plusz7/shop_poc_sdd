package com.project.custom.payment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, UUID> {

    Optional<PaymentJpaEntity> findByStripeSessionId(String stripeSessionId);

    List<PaymentJpaEntity> findByGuestIdAndStatusOrderByCreatedAtAsc(UUID guestId, String status);
}

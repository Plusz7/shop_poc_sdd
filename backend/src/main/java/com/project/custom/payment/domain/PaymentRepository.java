package com.project.custom.payment.domain;

import com.project.custom.shared.domain.GuestId;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Optional<Payment> findById(PaymentId id);

    Optional<Payment> findByProviderSessionId(String providerSessionId);

    /** The guest's payments whose provider session is still open (R-13). */
    List<Payment> findOpenByGuest(GuestId guestId);

    /**
     * Stores the payment. Fails with an optimistic locking error when it was changed concurrently since it
     * was loaded.
     */
    void save(Payment payment);
}

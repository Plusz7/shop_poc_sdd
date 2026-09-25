package com.project.custom.payment;

import com.project.custom.shared.domain.GuestId;

/**
 * API of the payment context for other bounded contexts (order). Both operations call the payment provider,
 * so they must be called outside a database transaction.
 */
public interface PaymentFacade {

    /**
     * Creates a hosted payment session for the order.
     *
     * @throws PaymentUnavailableException when the provider cannot be reached; the payment is marked failed
     */
    StartedPaymentDto start(StartPaymentDto request);

    /**
     * Expires the guest's still open payment sessions, so that an earlier order cannot be paid in parallel with
     * a new one (R-13). Each expired payment is reported with {@link PaymentFailedEvent}.
     */
    ExpiryResultDto expireOpen(GuestId guestId);
}

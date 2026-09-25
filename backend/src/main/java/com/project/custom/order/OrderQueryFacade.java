package com.project.custom.order;

import com.project.custom.shared.domain.GuestId;

import java.util.Optional;

/**
 * Read API of the order context for other bounded contexts (the future fulfillment feature).
 */
public interface OrderQueryFacade {

    /**
     * The order, only for the guest who placed it (FR-023); empty when the number is invalid, the order does
     * not exist or belongs to another guest (not distinguished, R-15).
     */
    Optional<OrderDto> get(String number, GuestId guestId);
}

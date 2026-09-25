package com.project.custom.cart;

import com.project.custom.shared.domain.GuestId;

/**
 * Write API of the cart for other bounded contexts (order).
 */
public interface CartCommandFacade {

    /** Removes all lines of the guest's cart in the caller's transaction (FR-021). */
    void clear(GuestId guestId);
}

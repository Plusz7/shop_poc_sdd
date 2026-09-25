package com.project.custom.cart;

import com.project.custom.shared.domain.GuestId;

/**
 * Read API of the cart for other bounded contexts (order).
 */
public interface CartQueryFacade {

    /** The guest's cart priced with current catalog data (FR-010); an empty cart when the guest has none. */
    PricedCartDto price(GuestId guestId);
}

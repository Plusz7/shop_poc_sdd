package com.project.custom.cart.domain;

import com.project.custom.shared.domain.GuestId;

import java.util.Optional;

public interface CartRepository {

    Optional<Cart> findByGuest(GuestId guestId);

    /**
     * Stores the cart and its lines. Fails with an optimistic locking error when the cart was changed
     * concurrently since it was loaded (two tabs of the same guest).
     */
    void save(Cart cart);
}

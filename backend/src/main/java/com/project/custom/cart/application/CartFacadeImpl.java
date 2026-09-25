package com.project.custom.cart.application;

import com.project.custom.cart.CartCommandFacade;
import com.project.custom.cart.CartQueryFacade;
import com.project.custom.cart.PricedCartDto;
import com.project.custom.shared.domain.GuestId;
import org.springframework.stereotype.Component;

/**
 * The cart facades delegate to the cart use cases, so that other contexts see exactly what the customer sees.
 */
@Component
class CartFacadeImpl implements CartQueryFacade, CartCommandFacade {

    private final CartService cartService;

    CartFacadeImpl(CartService cartService) {
        this.cartService = cartService;
    }

    @Override
    public PricedCartDto price(GuestId guestId) {
        return PricedCartMapper.toDto(cartService.price(guestId));
    }

    @Override
    public void clear(GuestId guestId) {
        cartService.clear(guestId);
    }
}

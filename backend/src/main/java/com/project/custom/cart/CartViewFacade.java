package com.project.custom.cart;

/**
 * The priced cart as the REST contract shows it (schema {@code Cart}, with customer-facing messages), for
 * API layers of other bounded contexts that return the cart in a response, e.g. {@code 409 SUMMARY_OUTDATED}.
 */
public interface CartViewFacade {

    CartViewDto view(PricedCartDto cart);
}

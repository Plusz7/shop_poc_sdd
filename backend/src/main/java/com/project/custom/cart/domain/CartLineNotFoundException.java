package com.project.custom.cart.domain;

/**
 * The cart has no line for the product whose quantity was to be changed.
 */
public class CartLineNotFoundException extends RuntimeException {

    public CartLineNotFoundException(long productId) {
        super("The cart has no line for product " + productId);
    }
}

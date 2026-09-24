package com.project.custom.cart.application;

/**
 * The product to add does not exist in the catalog.
 */
public class CartProductNotFoundException extends RuntimeException {

    public CartProductNotFoundException(long productId) {
        super("Product " + productId + " does not exist");
    }
}

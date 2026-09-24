package com.project.custom.cart.domain;

/**
 * The product is inactive or out of stock and cannot be added to the cart (US2-4).
 */
public class ProductUnavailableException extends RuntimeException {

    public ProductUnavailableException(long productId) {
        super("Product " + productId + " is unavailable");
    }
}

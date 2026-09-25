package com.project.custom.cart.domain;

/**
 * The product is inactive or out of stock: it cannot be added to the cart (US2-4) nor can its line quantity be
 * raised (US3-7).
 */
public class ProductUnavailableException extends RuntimeException {

    public ProductUnavailableException(long productId) {
        super("Product " + productId + " is unavailable");
    }
}

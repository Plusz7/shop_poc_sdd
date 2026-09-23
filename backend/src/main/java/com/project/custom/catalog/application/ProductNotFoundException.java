package com.project.custom.catalog.application;

/**
 * The product does not exist or is not active — both look the same to the customer.
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException() {
        super("Product not found");
    }
}

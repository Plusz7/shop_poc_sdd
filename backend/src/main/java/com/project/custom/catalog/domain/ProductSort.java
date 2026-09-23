package com.project.custom.catalog.domain;

/**
 * Allowed sort orders of the product list; every order uses the product id as a stable secondary key.
 */
public enum ProductSort {
    PRICE_ASC,
    PRICE_DESC,
    NAME
}

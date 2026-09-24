package com.project.custom.cart.domain;

/**
 * Availability of a product as the cart sees it, mirrored from the catalog status (FR-005).
 */
public enum ProductAvailability {
    AVAILABLE,
    LOW_STOCK,
    UNAVAILABLE
}

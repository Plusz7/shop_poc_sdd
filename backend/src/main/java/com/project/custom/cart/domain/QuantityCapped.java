package com.project.custom.cart.domain;

/**
 * Result of a quantity change larger than the product allows: the line was set to {@code maxQuantity}
 * instead (US3-3).
 */
public record QuantityCapped(int maxQuantity) {
}

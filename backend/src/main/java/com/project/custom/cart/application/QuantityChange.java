package com.project.custom.cart.application;

import com.project.custom.cart.domain.PricedCart;
import com.project.custom.cart.domain.QuantityCapped;

/**
 * Result of a quantity change: the freshly priced cart and, when the requested quantity was larger than
 * available, the cap applied (US3-3).
 *
 * @param productId the product whose line was changed
 * @param capped    {@code null} when the requested quantity was set as is
 */
public record QuantityChange(PricedCart cart, long productId, QuantityCapped capped) {
}

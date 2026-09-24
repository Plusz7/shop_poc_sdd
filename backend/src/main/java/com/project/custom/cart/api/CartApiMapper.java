package com.project.custom.cart.api;

import com.project.custom.cart.domain.PricedCart;
import com.project.custom.cart.domain.PricedLine;
import com.project.custom.cart.domain.ProductAvailability;

import java.util.List;

/**
 * Mapping of the priced cart to the contract schemas {@code Cart} and {@code CartLine} (openapi.yaml).
 * Price change notices, stock excess, {@code canPlaceOrder} and {@code messages} are computed by US3
 * (T075, T077); until then they report "no notice" and ordering stays disabled.
 */
final class CartApiMapper {

    private CartApiMapper() {
    }

    static CartResponse toResponse(PricedCart cart) {
        return new CartResponse(
                cart.lines().stream().map(CartApiMapper::toResponse).toList(),
                cart.itemCount(),
                cart.total().minor(),
                false,
                List.of());
    }

    private static CartLineResponse toResponse(PricedLine line) {
        return new CartLineResponse(line.productId(), line.name(), line.imageUrl(), line.unitPrice().minor(),
                line.quantity(), line.lineTotal().minor(), line.availability(), line.maxQuantity(), false, null,
                false);
    }

    record CartResponse(List<CartLineResponse> lines, int itemCount, long totalMinor, boolean canPlaceOrder,
                        List<MessageResponse> messages) {
    }

    record CartLineResponse(long productId, String name, String imageUrl, long unitPriceMinor, int quantity,
                            long lineTotalMinor, ProductAvailability status, int maxQuantity, boolean priceChanged,
                            Long previousPriceMinor, boolean quantityExceedsStock) {
    }

    record MessageResponse(String code, Long productId, String text) {
    }
}

package com.project.custom.cart.api;

import com.project.custom.cart.application.QuantityChange;
import com.project.custom.cart.domain.CartProblem;
import com.project.custom.cart.domain.PricedCart;
import com.project.custom.cart.domain.PricedLine;
import com.project.custom.cart.domain.ProductAvailability;
import com.project.custom.cart.domain.QuantityCapped;
import com.project.custom.shared.api.UiMessages;
import com.project.custom.shared.domain.Money;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Mapping of the priced cart to the contract schemas {@code Cart}, {@code CartLine} and {@code Message}
 * (openapi.yaml). Message texts are the customer-facing copy from {@link UiMessages}.
 */
@Component
class CartApiMapper {

    private static final String QUANTITY_CAPPED = "QUANTITY_CAPPED";

    private final UiMessages uiMessages;

    CartApiMapper(UiMessages uiMessages) {
        this.uiMessages = uiMessages;
    }

    CartResponse toResponse(PricedCart cart) {
        return toResponse(cart, List.of());
    }

    /** The cart after a quantity change; a cap applied comes first in {@code messages} (US3-3). */
    CartResponse toResponse(QuantityChange change) {
        QuantityCapped capped = change.capped();
        List<MessageResponse> actionMessages = capped == null ? List.of() : List.of(new MessageResponse(
                QUANTITY_CAPPED, change.productId(),
                uiMessages.get("cart.message.quantity-capped", capped.maxQuantity())));
        return toResponse(change.cart(), actionMessages);
    }

    private CartResponse toResponse(PricedCart cart, List<MessageResponse> actionMessages) {
        List<CartLineResponse> lines = cart.lines().stream().map(this::toResponse).toList();
        Map<Long, CartLineResponse> linesById = lines.stream()
                .collect(Collectors.toMap(CartLineResponse::productId, Function.identity()));
        List<MessageResponse> messages = new ArrayList<>(actionMessages);
        cart.problems().forEach(problem -> messages.add(toMessage(problem, linesById.get(problem.productId()))));
        return new CartResponse(lines, cart.itemCount(), cart.total().minor(), cart.canPlaceOrder(), messages);
    }

    private CartLineResponse toResponse(PricedLine line) {
        String name = line.name().isBlank() ? uiMessages.get("cart.removed-product") : line.name();
        return new CartLineResponse(line.productId(), name, line.imageUrl(), line.unitPrice().minor(),
                line.quantity(), line.lineTotal().minor(), line.availability(), line.maxQuantity(),
                line.priceChanged(), minorOrNull(line.previousPrice()), line.quantityExceedsStock());
    }

    private MessageResponse toMessage(CartProblem problem, CartLineResponse line) {
        String text = switch (problem.code()) {
            case PRICE_CHANGED -> uiMessages.get("cart.message.price-changed", line.name());
            case PRODUCT_UNAVAILABLE -> uiMessages.get("cart.message.product-unavailable", line.name());
            case QUANTITY_EXCEEDS_STOCK ->
                    uiMessages.get("cart.message.quantity-exceeds-stock", line.name(), line.maxQuantity());
        };
        return new MessageResponse(problem.code().name(), problem.productId(), text);
    }

    private static Long minorOrNull(Money money) {
        return money == null ? null : money.minor();
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

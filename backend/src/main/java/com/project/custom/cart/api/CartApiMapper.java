package com.project.custom.cart.api;

import com.project.custom.cart.CartViewDto;
import com.project.custom.cart.CartViewFacade;
import com.project.custom.cart.PricedCartDto;
import com.project.custom.cart.application.PricedCartMapper;
import com.project.custom.cart.application.QuantityChange;
import com.project.custom.cart.domain.PricedCart;
import com.project.custom.cart.domain.QuantityCapped;
import com.project.custom.shared.api.UiMessages;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Mapping of the priced cart to the contract schemas {@code Cart}, {@code CartLine} and {@code Message}
 * (openapi.yaml). Message texts are the customer-facing copy from {@link UiMessages}. Other contexts reuse it
 * through {@link CartViewFacade}.
 */
@Component
class CartApiMapper implements CartViewFacade {

    private static final String QUANTITY_CAPPED = "QUANTITY_CAPPED";

    private final UiMessages uiMessages;

    CartApiMapper(UiMessages uiMessages) {
        this.uiMessages = uiMessages;
    }

    CartViewDto toResponse(PricedCart cart) {
        return view(PricedCartMapper.toDto(cart));
    }

    /** The cart after a quantity change; a cap applied comes first in {@code messages} (US3-3). */
    CartViewDto toResponse(QuantityChange change) {
        QuantityCapped capped = change.capped();
        List<CartViewDto.Message> actionMessages = capped == null ? List.of() : List.of(new CartViewDto.Message(
                QUANTITY_CAPPED, change.productId(),
                uiMessages.get("cart.message.quantity-capped", capped.maxQuantity())));
        return toResponse(PricedCartMapper.toDto(change.cart()), actionMessages);
    }

    @Override
    public CartViewDto view(PricedCartDto cart) {
        return toResponse(cart, List.of());
    }

    private CartViewDto toResponse(PricedCartDto cart, List<CartViewDto.Message> actionMessages) {
        List<CartViewDto.Line> lines = cart.lines().stream().map(this::toResponse).toList();
        Map<Long, CartViewDto.Line> linesById = lines.stream()
                .collect(Collectors.toMap(CartViewDto.Line::productId, Function.identity()));
        List<CartViewDto.Message> messages = new ArrayList<>(actionMessages);
        cart.problems().forEach(problem -> messages.add(toMessage(problem, linesById.get(problem.productId()))));
        return new CartViewDto(lines, cart.itemCount(), cart.totalMinor(), cart.canPlaceOrder(), messages);
    }

    private CartViewDto.Line toResponse(PricedCartDto.Line line) {
        String name = line.name().isBlank() ? uiMessages.get("cart.removed-product") : line.name();
        return new CartViewDto.Line(line.productId(), name, line.imageUrl(), line.unitPriceMinor(),
                line.quantity(), line.lineTotalMinor(), line.status(), line.maxQuantity(),
                line.priceChanged(), line.previousPriceMinor(), line.quantityExceedsStock());
    }

    private CartViewDto.Message toMessage(PricedCartDto.Problem problem, CartViewDto.Line line) {
        String text = switch (problem.code()) {
            case "PRICE_CHANGED" -> uiMessages.get("cart.message.price-changed", line.name());
            case "PRODUCT_UNAVAILABLE" -> uiMessages.get("cart.message.product-unavailable", line.name());
            case "QUANTITY_EXCEEDS_STOCK" ->
                    uiMessages.get("cart.message.quantity-exceeds-stock", line.name(), line.maxQuantity());
            default -> throw new IllegalArgumentException("Unknown cart problem " + problem.code());
        };
        return new CartViewDto.Message(problem.code(), problem.productId(), text);
    }
}

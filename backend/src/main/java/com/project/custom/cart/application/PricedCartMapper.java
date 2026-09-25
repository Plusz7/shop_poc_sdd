package com.project.custom.cart.application;

import com.project.custom.cart.PricedCartDto;
import com.project.custom.cart.domain.PricedCart;
import com.project.custom.cart.domain.PricedLine;
import com.project.custom.shared.domain.Money;

/**
 * The only mapping of the priced cart read model to its public DTO, used by the facade and by the cart API.
 */
public final class PricedCartMapper {

    private PricedCartMapper() {
    }

    public static PricedCartDto toDto(PricedCart cart) {
        return new PricedCartDto(
                cart.lines().stream().map(PricedCartMapper::toDto).toList(),
                cart.itemCount(),
                cart.total().minor(),
                cart.canPlaceOrder(),
                cart.problems().stream()
                        .map(problem -> new PricedCartDto.Problem(problem.code().name(), problem.productId()))
                        .toList());
    }

    private static PricedCartDto.Line toDto(PricedLine line) {
        Money previousPrice = line.previousPrice();
        return new PricedCartDto.Line(line.productId(), line.name(), line.imageUrl(), line.unitPrice().minor(),
                line.quantity(), line.lineTotal().minor(), line.availability().name(), line.maxQuantity(),
                line.priceChanged(), previousPrice == null ? null : previousPrice.minor(),
                line.quantityExceedsStock());
    }
}

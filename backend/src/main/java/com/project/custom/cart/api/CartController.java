package com.project.custom.cart.api;

import com.project.custom.cart.api.CartApiMapper.CartResponse;
import com.project.custom.cart.application.CartProductNotFoundException;
import com.project.custom.cart.application.CartService;
import com.project.custom.cart.domain.CartLineNotFoundException;
import com.project.custom.cart.domain.ProductUnavailableException;
import com.project.custom.cart.domain.QuantityExceedsLimitException;
import com.project.custom.shared.api.ApiException;
import com.project.custom.shared.api.ErrorCode;
import com.project.custom.shared.domain.GuestId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
class CartController {

    private final CartService cartService;
    private final CartApiMapper mapper;

    CartController(CartService cartService, CartApiMapper mapper) {
        this.cartService = cartService;
        this.mapper = mapper;
    }

    @GetMapping
    CartResponse getCart(GuestId guestId) {
        return mapper.toResponse(cartService.price(guestId));
    }

    @DeleteMapping
    CartResponse clearCart(GuestId guestId) {
        return mapper.toResponse(cartService.clear(guestId));
    }

    /** Any price in the request body is ignored — only the catalog price counts (FR-010). */
    @PostMapping("/lines")
    CartResponse addLine(GuestId guestId, @Valid @RequestBody AddLineRequest request) {
        if (request.productId() <= 0) {
            throw ApiException.notFound();
        }
        try {
            return mapper.toResponse(cartService.add(guestId, request.productId(), request.quantity()));
        } catch (CartProductNotFoundException exception) {
            throw ApiException.notFound();
        } catch (ProductUnavailableException exception) {
            throw productUnavailable();
        } catch (QuantityExceedsLimitException exception) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.QUANTITY_EXCEEDS_LIMIT,
                    "error.quantity-exceeds-limit", exception.maxQuantity())
                    .withProperty("maxQuantity", exception.maxQuantity());
        }
    }

    /** A quantity above what is available is capped with a {@code QUANTITY_CAPPED} message; 0 removes the line. */
    @PutMapping("/lines/{productId}")
    CartResponse changeQuantity(GuestId guestId, @PathVariable long productId,
                                @Valid @RequestBody ChangeQuantityRequest request) {
        try {
            return mapper.toResponse(cartService.changeQuantity(guestId, productId, request.quantity()));
        } catch (CartLineNotFoundException exception) {
            throw ApiException.notFound();
        } catch (ProductUnavailableException exception) {
            throw productUnavailable();
        }
    }

    @DeleteMapping("/lines/{productId}")
    CartResponse removeLine(GuestId guestId, @PathVariable long productId) {
        return mapper.toResponse(cartService.remove(guestId, productId));
    }

    @PostMapping("/accept-prices")
    CartResponse acceptPrices(GuestId guestId) {
        return mapper.toResponse(cartService.acceptPrices(guestId));
    }

    private static ApiException productUnavailable() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.PRODUCT_UNAVAILABLE, "error.product-unavailable");
    }

    record AddLineRequest(
            @NotNull(message = "{validation.required}") Long productId,
            @NotNull(message = "{validation.cart-quantity}") @Min(value = 1, message = "{validation.cart-quantity}")
            @Max(value = 99, message = "{validation.cart-quantity}") Integer quantity) {
    }

    record ChangeQuantityRequest(
            @NotNull(message = "{validation.cart-quantity-change}")
            @Min(value = 0, message = "{validation.cart-quantity-change}")
            @Max(value = 99, message = "{validation.cart-quantity-change}") Integer quantity) {
    }
}

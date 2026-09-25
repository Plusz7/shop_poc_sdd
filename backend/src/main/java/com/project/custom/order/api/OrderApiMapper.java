package com.project.custom.order.api;

import com.project.custom.cart.CartViewFacade;
import com.project.custom.order.application.OrderRejectedException;
import com.project.custom.order.application.PlaceOrderCommand;
import com.project.custom.shared.api.ApiException;
import com.project.custom.shared.api.ErrorCode;
import com.project.custom.shared.domain.GuestId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapping between the order contract schemas ({@code PlaceOrderRequest}, {@code StartedPayment},
 * {@code ProblemWithCart}) and the order use cases. The current cart in a {@code 409} is rendered by the cart
 * context itself ({@link CartViewFacade}), so both contexts show it identically.
 */
@Component
class OrderApiMapper {

    private final CartViewFacade cartViewFacade;

    OrderApiMapper(CartViewFacade cartViewFacade) {
        this.cartViewFacade = cartViewFacade;
    }

    PlaceOrderCommand toCommand(GuestId guestId, PlaceOrderRequest request) {
        return new PlaceOrderCommand(guestId, request.email(), request.fullName(), request.streetAndNumber(),
                request.postalCode(), request.city(),
                request.confirmedSummary().lines().stream()
                        .map(line -> new PlaceOrderCommand.ConfirmedLine(line.productId(), line.quantity(),
                                line.unitPriceMinor()))
                        .toList(),
                request.confirmedSummary().totalMinor());
    }

    ApiException toApiException(OrderRejectedException rejected) {
        ApiException exception = switch (rejected.reason()) {
            case SUMMARY_OUTDATED ->
                    new ApiException(HttpStatus.CONFLICT, ErrorCode.SUMMARY_OUTDATED, "error.summary-outdated");
            case CART_NOT_ORDERABLE ->
                    new ApiException(HttpStatus.CONFLICT, ErrorCode.CART_NOT_ORDERABLE, "error.cart-not-orderable");
            case ALREADY_PAID ->
                    new ApiException(HttpStatus.CONFLICT, ErrorCode.ORDER_ALREADY_PAID, "error.order-already-paid");
            case PAYMENT_UNAVAILABLE -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCode.PAYMENT_UNAVAILABLE, "error.payment-unavailable");
            case INVALID_DETAILS ->
                    new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "error.validation");
        };
        return rejected.currentCart()
                .map(cart -> exception.withProperty("cart", cartViewFacade.view(cart)))
                .orElse(exception);
    }

    /** Contract schema {@code PlaceOrderRequest}; the country is not accepted, the shop delivers only in Poland. */
    record PlaceOrderRequest(
            @NotBlank(message = "{validation.email}") @Size(max = 254, message = "{validation.email}")
            @Email(message = "{validation.email}")
            @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "{validation.email}") String email,
            @NotBlank(message = "{validation.full-name}")
            @Size(min = 2, max = 100, message = "{validation.full-name}") String fullName,
            @NotBlank(message = "{validation.street}")
            @Size(min = 3, max = 120, message = "{validation.street}") String streetAndNumber,
            @NotNull(message = "{validation.postal-code}")
            @Pattern(regexp = "^\\d{2}-\\d{3}$", message = "{validation.postal-code}") String postalCode,
            @NotBlank(message = "{validation.city}")
            @Size(min = 2, max = 60, message = "{validation.city}") String city,
            @NotNull(message = "{validation.required}") @Valid ConfirmedSummary confirmedSummary) {
    }

    record ConfirmedSummary(
            @NotEmpty(message = "{validation.required}") List<@NotNull(message = "{validation.required}") @Valid ConfirmedLine> lines,
            @NotNull(message = "{validation.required}") Long totalMinor) {
    }

    record ConfirmedLine(
            @NotNull(message = "{validation.required}") Long productId,
            @NotNull(message = "{validation.cart-quantity}") @Min(value = 1, message = "{validation.cart-quantity}")
            @Max(value = 99, message = "{validation.cart-quantity}") Integer quantity,
            @NotNull(message = "{validation.required}") Long unitPriceMinor) {
    }

    /** Contract schema {@code StartedPayment}. */
    record StartedPaymentResponse(String number, String paymentUrl) {
    }
}

package com.project.custom.order.api;

import com.project.custom.order.OrderDto;
import com.project.custom.order.api.OrderApiMapper.PlaceOrderRequest;
import com.project.custom.order.api.OrderApiMapper.StartedPaymentResponse;
import com.project.custom.order.application.OrderQueryService;
import com.project.custom.order.application.OrderRejectedException;
import com.project.custom.order.application.PlaceOrderService;
import com.project.custom.order.application.PlacedOrder;
import com.project.custom.shared.api.ApiException;
import com.project.custom.shared.domain.GuestId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final PlaceOrderService placeOrderService;
    private final OrderQueryService orderQueryService;
    private final OrderApiMapper mapper;

    OrderController(PlaceOrderService placeOrderService, OrderQueryService orderQueryService, OrderApiMapper mapper) {
        this.placeOrderService = placeOrderService;
        this.orderQueryService = orderQueryService;
        this.mapper = mapper;
    }

    /** The amount is always priced on the server; the confirmed summary is only compared (R-14). */
    @PostMapping
    ResponseEntity<StartedPaymentResponse> placeOrder(GuestId guestId, @Valid @RequestBody PlaceOrderRequest request) {
        try {
            PlacedOrder placed = placeOrderService.place(mapper.toCommand(guestId, request));
            return ResponseEntity.created(URI.create("/api/orders/" + placed.number()))
                    .body(new StartedPaymentResponse(placed.number(), placed.paymentUrl()));
        } catch (OrderRejectedException rejected) {
            throw mapper.toApiException(rejected);
        }
    }

    /** {@code 404} both for an order that does not exist and for another guest's order (R-15). */
    @GetMapping("/{number}")
    OrderDto getOrder(GuestId guestId, @PathVariable String number) {
        return orderQueryService.get(number, guestId).orElseThrow(ApiException::notFound);
    }
}

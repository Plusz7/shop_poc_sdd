package com.project.custom.order.application;

import com.project.custom.order.OrderDto;
import com.project.custom.order.OrderQueryFacade;
import com.project.custom.shared.domain.GuestId;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class OrderQueryFacadeImpl implements OrderQueryFacade {

    private final OrderQueryService orderQueryService;

    OrderQueryFacadeImpl(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    @Override
    public Optional<OrderDto> get(String number, GuestId guestId) {
        return orderQueryService.get(number, guestId);
    }
}

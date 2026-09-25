package com.project.custom.order.application;

import com.project.custom.order.OrderDto;
import com.project.custom.order.domain.Order;
import com.project.custom.order.domain.OrderNumber;
import com.project.custom.order.domain.OrderRepository;
import com.project.custom.shared.domain.GuestId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reading an order for the confirmation page (US4-6). Read-only: visiting the page never changes the order or
 * the payment (FR-019).
 */
@Service
public class OrderQueryService {

    private static final Pattern NUMBER = Pattern.compile(OrderNumber.PATTERN);

    private final OrderRepository orderRepository;

    OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** Empty when the number is invalid, the order does not exist or belongs to another guest (R-15). */
    @Transactional(readOnly = true)
    public Optional<OrderDto> get(String number, GuestId guestId) {
        if (number == null || !NUMBER.matcher(number).matches()) {
            return Optional.empty();
        }
        return orderRepository.findByNumber(new OrderNumber(number))
                .filter(order -> order.isOwnedBy(guestId))
                .map(OrderQueryService::toDto);
    }

    private static OrderDto toDto(Order order) {
        return new OrderDto(
                order.number().value(),
                order.status().name(),
                order.lines().stream()
                        .map(line -> new OrderDto.Line(line.name(), line.unitPrice().minor(), line.quantity(),
                                line.lineTotal().minor()))
                        .toList(),
                order.total().minor(),
                order.createdAt(),
                order.paidAt().orElse(null));
    }
}

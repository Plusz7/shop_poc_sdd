package com.project.custom.order.application;

import com.project.custom.cart.CartQueryFacade;
import com.project.custom.cart.PricedCartDto;
import com.project.custom.order.application.OrderRejectedException.Reason;
import com.project.custom.order.domain.CustomerDetails;
import com.project.custom.order.domain.Order;
import com.project.custom.order.domain.OrderId;
import com.project.custom.order.domain.OrderLine;
import com.project.custom.order.domain.OrderNumberGenerator;
import com.project.custom.order.domain.OrderRepository;
import com.project.custom.order.domain.OrderStatus;
import com.project.custom.order.domain.ShippingAddress;
import com.project.custom.payment.PaymentFacade;
import com.project.custom.payment.PaymentUnavailableException;
import com.project.custom.payment.StartPaymentDto;
import com.project.custom.shared.domain.Money;
import com.project.custom.shared.domain.PiiMasking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Placing an order and starting its payment (US4, FR-014–FR-018; plan.md "Key flows" 1). The cart is
 * re-priced and compared with the summary the customer confirmed; the order is stored with server-side prices
 * only, and the payment provider is called outside any database transaction. The cart is not cleared here:
 * only a verified payment clears it (FR-021, FR-022).
 */
@Service
public class PlaceOrderService {

    private static final Logger log = LoggerFactory.getLogger(PlaceOrderService.class);

    private final CartQueryFacade cartQueryFacade;
    private final PaymentFacade paymentFacade;
    private final OrderRepository orderRepository;
    private final OrderNumberGenerator orderNumberGenerator;
    private final OrderMetrics orderMetrics;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    PlaceOrderService(CartQueryFacade cartQueryFacade, PaymentFacade paymentFacade, OrderRepository orderRepository,
                      OrderNumberGenerator orderNumberGenerator, OrderMetrics orderMetrics,
                      TransactionTemplate transactionTemplate, Clock clock) {
        this.cartQueryFacade = cartQueryFacade;
        this.paymentFacade = paymentFacade;
        this.orderRepository = orderRepository;
        this.orderNumberGenerator = orderNumberGenerator;
        this.orderMetrics = orderMetrics;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /**
     * @throws OrderRejectedException when the order cannot be placed; no order is left awaiting payment
     */
    public PlacedOrder place(PlaceOrderCommand command) {
        CustomerDetails customer;
        ShippingAddress address;
        try {
            customer = new CustomerDetails(command.email(), command.fullName());
            address = ShippingAddress.inPoland(command.streetAndNumber(), command.postalCode(), command.city());
        } catch (IllegalArgumentException invalid) {
            throw new OrderRejectedException(Reason.INVALID_DETAILS);
        }

        PricedCartDto cart = cartQueryFacade.price(command.guestId());
        if (cart.lines().isEmpty()) {
            throw new OrderRejectedException(Reason.CART_NOT_ORDERABLE, cart);
        }
        if (!matchesConfirmedSummary(cart, command)) {
            orderMetrics.summaryMismatch(mismatchKinds(cart, command));
            throw new OrderRejectedException(Reason.SUMMARY_OUTDATED, cart);
        }
        if (!cart.canPlaceOrder()) {
            throw new OrderRejectedException(Reason.CART_NOT_ORDERABLE, cart);
        }

        switch (paymentFacade.expireOpen(command.guestId())) {
            case ALREADY_PAID -> throw new OrderRejectedException(Reason.ALREADY_PAID);
            case UNAVAILABLE -> throw new OrderRejectedException(Reason.PAYMENT_UNAVAILABLE);
            case EXPIRED -> {
                // nothing of the guest can be paid in parallel any more
            }
        }

        Order order = Order.place(OrderId.random(), orderNumberGenerator.next(), command.guestId(), customer, address,
                orderLines(cart), clock.instant());
        transactionTemplate.executeWithoutResult(status -> {
            orderRepository.save(order);
            orderMetrics.orderCreated();
        });
        log.info("Order {} placed for {} at {}, total {} grosze", order.number(), PiiMasking.email(customer.email()),
                PiiMasking.address(address.streetAndNumber(), address.postalCode(), address.city()),
                order.total().minor());

        try {
            String paymentUrl = paymentFacade.start(startPayment(order)).paymentUrl();
            return new PlacedOrder(order.number().value(), paymentUrl);
        } catch (PaymentUnavailableException unavailable) {
            markPaymentFailed(order.id());
            throw new OrderRejectedException(Reason.PAYMENT_UNAVAILABLE);
        } catch (RuntimeException failure) {
            markPaymentFailed(order.id());
            throw failure;
        }
    }

    /**
     * The customer confirmed exactly these products, quantities, unit prices and total (R-14). Availability
     * changes are reported through {@link PricedCartDto#canPlaceOrder()}.
     */
    private static boolean matchesConfirmedSummary(PricedCartDto cart, PlaceOrderCommand command) {
        if (cart.totalMinor() != command.confirmedTotalMinor()
                || cart.lines().size() != command.confirmedLines().size()) {
            return false;
        }
        Map<Long, PlaceOrderCommand.ConfirmedLine> confirmed = command.confirmedLines().stream()
                .collect(Collectors.toMap(PlaceOrderCommand.ConfirmedLine::productId, Function.identity(),
                        (first, duplicate) -> first));
        return cart.lines().stream().allMatch(line -> {
            PlaceOrderCommand.ConfirmedLine seen = confirmed.get(line.productId());
            return seen != null && seen.quantity() == line.quantity() && seen.unitPriceMinor() == line.unitPriceMinor();
        });
    }

    /**
     * What differs between the confirmed summary and the fresh pricing, for metrics only (data-model.md,
     * Observability). A difference in the total alone is reported as {@code PRICE}.
     */
    private static Set<MismatchKind> mismatchKinds(PricedCartDto cart, PlaceOrderCommand command) {
        Map<Long, PlaceOrderCommand.ConfirmedLine> confirmed = command.confirmedLines().stream()
                .collect(Collectors.toMap(PlaceOrderCommand.ConfirmedLine::productId, Function.identity(),
                        (first, duplicate) -> first));
        Set<MismatchKind> kinds = EnumSet.noneOf(MismatchKind.class);
        if (cart.lines().size() != confirmed.size()) {
            kinds.add(MismatchKind.CONTENTS);
        }
        for (PricedCartDto.Line line : cart.lines()) {
            if (!line.isAvailable() || line.quantityExceedsStock()) {
                kinds.add(MismatchKind.AVAILABILITY);
            }
            PlaceOrderCommand.ConfirmedLine seen = confirmed.get(line.productId());
            if (seen == null || seen.quantity() != line.quantity()) {
                kinds.add(MismatchKind.CONTENTS);
            } else if (seen.unitPriceMinor() != line.unitPriceMinor()) {
                kinds.add(MismatchKind.PRICE);
            }
        }
        if (kinds.isEmpty()) {
            kinds.add(MismatchKind.PRICE);
        }
        return kinds;
    }

    private static List<OrderLine> orderLines(PricedCartDto cart) {
        List<OrderLine> lines = new ArrayList<>();
        for (PricedCartDto.Line line : cart.lines()) {
            lines.add(new OrderLine(lines.size() + 1, line.productId(), line.name(), Money.pln(line.unitPriceMinor()),
                    line.quantity()));
        }
        return lines;
    }

    private static StartPaymentDto startPayment(Order order) {
        return new StartPaymentDto(order.id().value(), order.number().value(), order.guestId(),
                order.customer().email(),
                order.lines().stream()
                        .map(line -> new StartPaymentDto.Line(line.name(), line.unitPrice().minor(), line.quantity()))
                        .toList(),
                order.total().minor());
    }

    private void markPaymentFailed(OrderId orderId) {
        transactionTemplate.executeWithoutResult(status -> orderRepository.findById(orderId)
                .filter(order -> order.status() == OrderStatus.AWAITING_PAYMENT)
                .ifPresent(order -> {
                    order.markPaymentFailed();
                    orderRepository.save(order);
                    orderMetrics.orderCompleted(OrderStatus.PAYMENT_FAILED, order.total(),
                            Duration.between(order.createdAt(), clock.instant()));
                }));
    }
}

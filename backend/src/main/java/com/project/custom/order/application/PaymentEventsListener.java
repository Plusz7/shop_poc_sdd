package com.project.custom.order.application;

import com.project.custom.cart.CartCommandFacade;
import com.project.custom.catalog.CatalogCommandFacade;
import com.project.custom.catalog.StockDecreaseResult;
import com.project.custom.catalog.StockLineDto;
import com.project.custom.order.OrderPaidEvent;
import com.project.custom.order.domain.ConfirmationOutcome;
import com.project.custom.order.domain.Order;
import com.project.custom.order.domain.OrderId;
import com.project.custom.order.domain.OrderLine;
import com.project.custom.order.domain.OrderRepository;
import com.project.custom.order.domain.OrderStatus;
import com.project.custom.payment.PaymentConfirmedEvent;
import com.project.custom.payment.PaymentFailedEvent;
import com.project.custom.shared.domain.outbox.OutboxEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Reacts to payment outcomes within the webhook transaction (R-02, data-model.md state machine): a
 * confirmation decreases the stock, marks the order paid or for review, clears the cart and writes
 * {@link OrderPaidEvent} to the outbox, all or nothing (FR-021). A failure leaves the cart untouched (FR-022).
 */
@Component
class PaymentEventsListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventsListener.class);

    private final OrderRepository orderRepository;
    private final CatalogCommandFacade catalogCommandFacade;
    private final CartCommandFacade cartCommandFacade;
    private final OutboxEventPublisher outboxEventPublisher;
    private final OrderMetrics orderMetrics;

    PaymentEventsListener(OrderRepository orderRepository, CatalogCommandFacade catalogCommandFacade,
                          CartCommandFacade cartCommandFacade, OutboxEventPublisher outboxEventPublisher,
                          OrderMetrics orderMetrics) {
        this.orderRepository = orderRepository;
        this.catalogCommandFacade = catalogCommandFacade;
        this.cartCommandFacade = cartCommandFacade;
        this.outboxEventPublisher = outboxEventPublisher;
        this.orderMetrics = orderMetrics;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void on(PaymentConfirmedEvent event) {
        Order order = load(event.orderId());
        OrderStatus before = order.status();
        ConfirmationOutcome outcome = order.confirmPayment(event.amountMinor(), event.currency(), this::decreaseStock,
                event.confirmedAt());
        if (outcome == ConfirmationOutcome.UNCHANGED) {
            return;
        }
        orderRepository.save(order);
        if (before == OrderStatus.AWAITING_PAYMENT) {
            cartCommandFacade.clear(order.guestId());
        }
        orderMetrics.orderCompleted(order.status(), order.total(),
                Duration.between(order.createdAt(), order.paidAt().orElse(event.confirmedAt())));
        if (outcome == ConfirmationOutcome.PAID) {
            outboxEventPublisher.publish(OrderPaidEvent.TYPE, order.number().value(), paidEvent(order));
            log.info("Order {} paid", order.number());
        } else {
            log.warn("Order {} paid but needs review: {}", order.number(), order.reviewReason().orElseThrow());
        }
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void on(PaymentFailedEvent event) {
        Order order = load(event.orderId());
        if (order.status() != OrderStatus.AWAITING_PAYMENT) {
            return;
        }
        order.markPaymentFailed();
        orderRepository.save(order);
        orderMetrics.orderCompleted(OrderStatus.PAYMENT_FAILED, order.total(), Duration.ZERO);
        log.info("Payment of order {} ended without success: {}", order.number(), event.reason());
    }

    private boolean decreaseStock(List<OrderLine> lines) {
        return catalogCommandFacade.decreaseStock(lines.stream()
                .map(line -> new StockLineDto(line.productId(), line.quantity()))
                .toList()) == StockDecreaseResult.DECREASED;
    }

    private Order load(UUID orderId) {
        return orderRepository.findById(new OrderId(orderId))
                .orElseThrow(() -> new IllegalStateException("Order " + orderId + " of a payment does not exist"));
    }

    private static OrderPaidEvent paidEvent(Order order) {
        return new OrderPaidEvent(
                order.number().value(),
                order.paidAt().orElseThrow(),
                new OrderPaidEvent.Total(order.total().minor(), order.total().currency().getCurrencyCode()),
                order.lines().stream()
                        .map(line -> new OrderPaidEvent.Line(line.name(), line.quantity(), line.unitPrice().minor()))
                        .toList(),
                new OrderPaidEvent.Customer(order.customer().fullName()),
                new OrderPaidEvent.ShippingAddress(order.shippingAddress().streetAndNumber(),
                        order.shippingAddress().postalCode(), order.shippingAddress().city(),
                        order.shippingAddress().country()));
    }
}

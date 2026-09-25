package com.project.custom.order.domain;

import com.project.custom.shared.domain.GuestId;
import com.project.custom.shared.domain.Money;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The order aggregate: an immutable copy of the priced cart lines with the customer's details, moved through
 * its lifecycle only by payment outcomes (data-model.md, state machine).
 */
public class Order {

    private static final String PAYMENT_CURRENCY = "pln";

    private final OrderId id;
    private final OrderNumber number;
    private final GuestId guestId;
    private final CustomerDetails customer;
    private final ShippingAddress shippingAddress;
    private final List<OrderLine> lines;
    private final Money total;
    private final Instant createdAt;
    private final long version;
    private OrderStatus status;
    private Instant paidAt;
    private ReviewReason reviewReason;

    private Order(OrderId id, OrderNumber number, GuestId guestId, CustomerDetails customer,
                  ShippingAddress shippingAddress, List<OrderLine> lines, OrderStatus status, Instant createdAt,
                  Instant paidAt, ReviewReason reviewReason, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.number = Objects.requireNonNull(number, "number");
        this.guestId = Objects.requireNonNull(guestId, "guestId");
        this.customer = Objects.requireNonNull(customer, "customer");
        this.shippingAddress = Objects.requireNonNull(shippingAddress, "shippingAddress");
        this.lines = List.copyOf(lines);
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.paidAt = paidAt;
        this.reviewReason = reviewReason;
        this.version = version;
        if (this.lines.isEmpty()) {
            throw new IllegalArgumentException("An order has at least one line");
        }
        if (this.lines.stream().map(OrderLine::lineNo).distinct().count() != this.lines.size()) {
            throw new IllegalArgumentException("Order line numbers must be unique");
        }
        this.total = this.lines.stream().map(OrderLine::lineTotal).reduce(Money.ZERO, Money::plus);
    }

    /** A new order awaiting payment; the total is the sum of the line totals, shipping is free (FR-024). */
    public static Order place(OrderId id, OrderNumber number, GuestId guestId, CustomerDetails customer,
                              ShippingAddress shippingAddress, List<OrderLine> lines, Instant now) {
        return new Order(id, number, guestId, customer, shippingAddress, lines, OrderStatus.AWAITING_PAYMENT, now,
                null, null, 0);
    }

    public static Order restore(OrderId id, OrderNumber number, GuestId guestId, CustomerDetails customer,
                                ShippingAddress shippingAddress, List<OrderLine> lines, OrderStatus status,
                                Instant createdAt, Instant paidAt, ReviewReason reviewReason, long version) {
        return new Order(id, number, guestId, customer, shippingAddress, lines, status, createdAt, paidAt,
                reviewReason, version);
    }

    /**
     * Applies a verified payment confirmation. Stock is decreased only when the charged amount and currency
     * match the total (SC-004); a late confirmation of a failed order goes to review without touching stock.
     *
     * @param amountMinor charged amount in minor units, as reported by the payment provider
     * @param currency    charged currency (ISO code, any case)
     * @throws IllegalStatusTransition when the order is already in review
     */
    public ConfirmationOutcome confirmPayment(long amountMinor, String currency, StockAllocation stock, Instant now) {
        return switch (status) {
            case PAID -> ConfirmationOutcome.UNCHANGED;
            case NEEDS_REVIEW -> throw new IllegalStatusTransition(status, "confirm payment of");
            case PAYMENT_FAILED -> review(ReviewReason.CONFIRMED_AFTER_FAILURE, now);
            case AWAITING_PAYMENT -> {
                if (amountMinor != total.minor() || !PAYMENT_CURRENCY.equalsIgnoreCase(currency)) {
                    yield review(ReviewReason.AMOUNT_MISMATCH, now);
                }
                if (!stock.decrease(lines)) {
                    yield review(ReviewReason.INSUFFICIENT_STOCK, now);
                }
                status = OrderStatus.PAID;
                paidAt = now;
                yield ConfirmationOutcome.PAID;
            }
        };
    }

    /**
     * The payment failed, expired or could not be started; the cart is not touched (FR-022).
     *
     * @throws IllegalStatusTransition unless the order is awaiting payment
     */
    public void markPaymentFailed() {
        if (status != OrderStatus.AWAITING_PAYMENT) {
            throw new IllegalStatusTransition(status, "fail payment of");
        }
        status = OrderStatus.PAYMENT_FAILED;
    }

    public boolean isOwnedBy(GuestId guest) {
        return guestId.equals(guest);
    }

    private ConfirmationOutcome review(ReviewReason reason, Instant now) {
        status = OrderStatus.NEEDS_REVIEW;
        reviewReason = reason;
        paidAt = now;
        return ConfirmationOutcome.NEEDS_REVIEW;
    }

    public OrderId id() {
        return id;
    }

    public OrderNumber number() {
        return number;
    }

    public GuestId guestId() {
        return guestId;
    }

    public CustomerDetails customer() {
        return customer;
    }

    public ShippingAddress shippingAddress() {
        return shippingAddress;
    }

    public List<OrderLine> lines() {
        return lines;
    }

    public Money total() {
        return total;
    }

    public OrderStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<Instant> paidAt() {
        return Optional.ofNullable(paidAt);
    }

    public Optional<ReviewReason> reviewReason() {
        return Optional.ofNullable(reviewReason);
    }

    /** Optimistic locking version of the loaded state. */
    public long version() {
        return version;
    }
}

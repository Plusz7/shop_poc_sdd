package com.project.custom.order.domain;

import com.project.custom.shared.domain.GuestId;
import com.project.custom.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Instant CREATED = Instant.parse("2026-09-25T10:00:00Z");
    private static final Instant PAID_AT = Instant.parse("2026-09-25T10:03:00Z");
    private static final Instant LATER = Instant.parse("2026-09-25T10:30:00Z");

    private final RecordingStock stock = new RecordingStock(true);

    @Test
    void newOrderAwaitsPaymentAndTotalsItsLines() {
        Order order = order();

        assertThat(order.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(order.total()).isEqualTo(Money.pln(2 * 12_900 + 5_000));
        assertThat(order.createdAt()).isEqualTo(CREATED);
        assertThat(order.paidAt()).isEmpty();
        assertThat(order.reviewReason()).isEmpty();
    }

    @Test
    void orderNeedsAtLeastOneLine() {
        assertThatThrownBy(() -> Order.place(OrderId.random(), number(), GuestId.random(), customer(), address(),
                List.of(), CREATED)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void linesAreACopyThatCannotBeChanged() {
        List<OrderLine> lines = new ArrayList<>(lines());
        Order order = Order.place(OrderId.random(), number(), GuestId.random(), customer(), address(), lines, CREATED);

        lines.clear();

        assertThat(order.lines()).hasSize(2);
        assertThatThrownBy(() -> order.lines().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void orderLineValidatesPriceAndQuantity() {
        assertThatThrownBy(() -> new OrderLine(1, 7, "Kubek", Money.pln(0), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderLine(1, 7, "Kubek", Money.pln(100), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderLine(1, 7, "Kubek", Money.pln(100), 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new OrderLine(1, 7, "Kubek", Money.pln(100), 3).lineTotal()).isEqualTo(Money.pln(300));
    }

    @Test
    void matchingConfirmationWithStockMarksOrderPaid() {
        Order order = order();

        ConfirmationOutcome outcome = order.confirmPayment(order.total().minor(), "pln", stock, PAID_AT);

        assertThat(outcome).isEqualTo(ConfirmationOutcome.PAID);
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.paidAt()).contains(PAID_AT);
        assertThat(order.reviewReason()).isEmpty();
        assertThat(stock.calls).containsExactly(order.lines());
    }

    @Test
    void currencyIsComparedCaseInsensitively() {
        Order order = order();

        assertThat(order.confirmPayment(order.total().minor(), "PLN", stock, PAID_AT)).isEqualTo(ConfirmationOutcome.PAID);
    }

    @Test
    void insufficientStockSendsOrderToReview() {
        Order order = order();

        ConfirmationOutcome outcome = order.confirmPayment(order.total().minor(), "pln", new RecordingStock(false), PAID_AT);

        assertThat(outcome).isEqualTo(ConfirmationOutcome.NEEDS_REVIEW);
        assertThat(order.status()).isEqualTo(OrderStatus.NEEDS_REVIEW);
        assertThat(order.reviewReason()).contains(ReviewReason.INSUFFICIENT_STOCK);
        assertThat(order.paidAt()).contains(PAID_AT);
    }

    @Test
    void amountMismatchSendsOrderToReviewWithoutTouchingStock() {
        Order order = order();

        ConfirmationOutcome outcome = order.confirmPayment(order.total().minor() - 1, "pln", stock, PAID_AT);

        assertThat(outcome).isEqualTo(ConfirmationOutcome.NEEDS_REVIEW);
        assertThat(order.reviewReason()).contains(ReviewReason.AMOUNT_MISMATCH);
        assertThat(stock.calls).isEmpty();
    }

    @Test
    void currencyMismatchSendsOrderToReviewWithoutTouchingStock() {
        Order order = order();

        order.confirmPayment(order.total().minor(), "eur", stock, PAID_AT);

        assertThat(order.status()).isEqualTo(OrderStatus.NEEDS_REVIEW);
        assertThat(order.reviewReason()).contains(ReviewReason.AMOUNT_MISMATCH);
        assertThat(stock.calls).isEmpty();
    }

    @Test
    void failedPaymentMarksOrderFailed() {
        Order order = order();

        order.markPaymentFailed();

        assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(order.paidAt()).isEmpty();
    }

    @Test
    void lateConfirmationAfterFailureSendsOrderToReviewWithoutTouchingStock() {
        Order order = order();
        order.markPaymentFailed();

        ConfirmationOutcome outcome = order.confirmPayment(order.total().minor(), "pln", stock, LATER);

        assertThat(outcome).isEqualTo(ConfirmationOutcome.NEEDS_REVIEW);
        assertThat(order.status()).isEqualTo(OrderStatus.NEEDS_REVIEW);
        assertThat(order.reviewReason()).contains(ReviewReason.CONFIRMED_AFTER_FAILURE);
        assertThat(order.paidAt()).contains(LATER);
        assertThat(stock.calls).isEmpty();
    }

    @Test
    void repeatedConfirmationOfPaidOrderHasNoEffects() {
        Order order = order();
        order.confirmPayment(order.total().minor(), "pln", stock, PAID_AT);

        ConfirmationOutcome outcome = order.confirmPayment(order.total().minor(), "pln", stock, LATER);

        assertThat(outcome).isEqualTo(ConfirmationOutcome.UNCHANGED);
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.paidAt()).contains(PAID_AT);
        assertThat(stock.calls).hasSize(1);
    }

    @Test
    void paidOrderCannotFail() {
        Order order = order();
        order.confirmPayment(order.total().minor(), "pln", stock, PAID_AT);

        assertThatThrownBy(order::markPaymentFailed).isInstanceOf(IllegalStatusTransition.class);
    }

    @Test
    void orderInReviewCannotFail() {
        Order order = order();
        order.confirmPayment(1, "pln", stock, PAID_AT);

        assertThatThrownBy(order::markPaymentFailed).isInstanceOf(IllegalStatusTransition.class);
    }

    @Test
    void failedOrderCannotFailAgain() {
        Order order = order();
        order.markPaymentFailed();

        assertThatThrownBy(order::markPaymentFailed).isInstanceOf(IllegalStatusTransition.class);
    }

    @Test
    void orderInReviewCannotBeConfirmedAgain() {
        Order order = order();
        order.confirmPayment(1, "pln", stock, PAID_AT);

        assertThatThrownBy(() -> order.confirmPayment(order.total().minor(), "pln", stock, LATER))
                .isInstanceOf(IllegalStatusTransition.class);
    }

    @Test
    void ownershipIsCheckedByGuest() {
        GuestId owner = GuestId.random();
        Order order = Order.place(OrderId.random(), number(), owner, customer(), address(), lines(), CREATED);

        assertThat(order.isOwnedBy(owner)).isTrue();
        assertThat(order.isOwnedBy(GuestId.random())).isFalse();
    }

    private static Order order() {
        return Order.place(OrderId.random(), number(), GuestId.random(), customer(), address(), lines(), CREATED);
    }

    private static List<OrderLine> lines() {
        return List.of(
                new OrderLine(1, 7, "Kubek", Money.pln(12_900), 2),
                new OrderLine(2, 9, "Talerz", Money.pln(5_000), 1));
    }

    private static OrderNumber number() {
        return new OrderNumber("ORD-7K2Q9M4XTB");
    }

    private static CustomerDetails customer() {
        return new CustomerDetails("jan@example.com", "Jan Kowalski");
    }

    private static ShippingAddress address() {
        return ShippingAddress.inPoland("ul. Długa 5", "80-001", "Gdańsk");
    }

    private static final class RecordingStock implements StockAllocation {

        private final boolean available;
        private final List<List<OrderLine>> calls = new ArrayList<>();

        private RecordingStock(boolean available) {
            this.available = available;
        }

        @Override
        public boolean decrease(List<OrderLine> lines) {
            calls.add(lines);
            return available;
        }
    }
}

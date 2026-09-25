package com.project.custom.payment.domain;

import com.project.custom.shared.domain.Money;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What the provider needs to show the hosted payment page for an order.
 *
 * @param total the order total; the sum of the line totals must equal it (SC-004)
 */
public record SessionRequest(PaymentId paymentId, UUID orderId, String orderNumber, List<Line> lines,
                             String customerEmail, Money total) {

    public SessionRequest {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(orderNumber, "orderNumber");
        Objects.requireNonNull(customerEmail, "customerEmail");
        Objects.requireNonNull(total, "total");
        lines = List.copyOf(lines);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("A payment session needs at least one line");
        }
    }

    /** Sum of {@code unitPrice × quantity} of all lines. */
    public Money linesTotal() {
        return lines.stream().map(line -> line.unitPrice().times(line.quantity())).reduce(Money.ZERO, Money::plus);
    }

    public record Line(String name, Money unitPrice, int quantity) {

        public Line {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(unitPrice, "unitPrice");
        }
    }
}

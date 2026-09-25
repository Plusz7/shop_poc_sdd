package com.project.custom.order;

import java.time.Instant;
import java.util.List;

/**
 * An order as the customer sees it on the confirmation page (contract schema {@code Order}).
 *
 * @param status {@code AWAITING_PAYMENT | PAID | PAYMENT_FAILED | NEEDS_REVIEW}
 * @param paidAt {@code null} until the payment is confirmed
 */
public record OrderDto(String number, String status, List<Line> lines, long totalMinor, Instant createdAt,
                       Instant paidAt) {

    public OrderDto {
        lines = List.copyOf(lines);
    }

    public record Line(String name, long unitPriceMinor, int quantity, long lineTotalMinor) {
    }
}

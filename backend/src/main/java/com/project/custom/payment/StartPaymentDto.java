package com.project.custom.payment;

import com.project.custom.shared.domain.GuestId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What is paid for: the order lines exactly as stored in the order (the amount always comes from server-side
 * pricing, Principle II).
 */
public record StartPaymentDto(UUID orderId, String orderNumber, GuestId guestId, String customerEmail,
                              List<Line> lines, long totalMinor) {

    public StartPaymentDto {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(orderNumber, "orderNumber");
        Objects.requireNonNull(guestId, "guestId");
        Objects.requireNonNull(customerEmail, "customerEmail");
        lines = List.copyOf(lines);
    }

    public record Line(String name, long unitPriceMinor, int quantity) {
    }
}

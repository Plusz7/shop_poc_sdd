package com.project.custom.order.application;

import com.project.custom.shared.domain.GuestId;

import java.util.List;
import java.util.Objects;

/**
 * The customer's checkout: delivery details and the summary they confirmed. The confirmed prices are used
 * only for comparison with a fresh pricing, never for the amount (R-14, Principle II).
 */
public record PlaceOrderCommand(GuestId guestId, String email, String fullName, String streetAndNumber,
                                String postalCode, String city, List<ConfirmedLine> confirmedLines,
                                long confirmedTotalMinor) {

    public PlaceOrderCommand {
        Objects.requireNonNull(guestId, "guestId");
        confirmedLines = List.copyOf(confirmedLines);
    }

    public record ConfirmedLine(long productId, int quantity, long unitPriceMinor) {
    }
}

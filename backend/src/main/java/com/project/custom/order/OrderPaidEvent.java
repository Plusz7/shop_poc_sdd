package com.project.custom.order;

import java.time.Instant;
import java.util.List;

/**
 * Outbox payload of a paid order (research R-17), for the fulfillment feature (Trello card). Contains no
 * email address (data minimization).
 */
public record OrderPaidEvent(String number, Instant paidAt, Total total, List<Line> lines, Customer customer,
                             ShippingAddress shippingAddress) {

    public static final String TYPE = "OrderPaid";

    public OrderPaidEvent {
        lines = List.copyOf(lines);
    }

    public record Total(long minor, String currency) {
    }

    public record Line(String name, int quantity, long unitPriceMinor) {
    }

    public record Customer(String fullName) {
    }

    public record ShippingAddress(String streetAndNumber, String postalCode, String city, String country) {
    }
}

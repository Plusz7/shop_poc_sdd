package com.project.custom.order.domain;

/**
 * The requested change is not allowed by the order state machine (data-model.md).
 */
public class IllegalStatusTransition extends RuntimeException {

    public IllegalStatusTransition(OrderStatus from, String change) {
        super("Cannot " + change + " an order in status " + from);
    }
}

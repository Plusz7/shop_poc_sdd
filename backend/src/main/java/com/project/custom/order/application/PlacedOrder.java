package com.project.custom.order.application;

/**
 * @param paymentUrl the hosted payment page the customer is redirected to
 */
public record PlacedOrder(String number, String paymentUrl) {
}

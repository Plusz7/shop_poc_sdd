package com.project.custom.payment;

/**
 * @param paymentUrl the hosted payment page the customer is redirected to
 */
public record StartedPaymentDto(String paymentUrl) {
}

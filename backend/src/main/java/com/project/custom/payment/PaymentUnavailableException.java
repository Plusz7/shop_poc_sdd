package com.project.custom.payment;

/**
 * The payment provider could not be reached after all attempts; the payment was not started.
 */
public class PaymentUnavailableException extends RuntimeException {

    public PaymentUnavailableException(String reason) {
        super(reason);
    }
}

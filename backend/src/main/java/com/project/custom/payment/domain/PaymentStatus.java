package com.project.custom.payment.domain;

public enum PaymentStatus {
    /** Stored before the provider session is created. */
    CREATED,
    /** The provider session is open; the customer can pay. */
    OPEN,
    CONFIRMED,
    FAILED,
    EXPIRED
}

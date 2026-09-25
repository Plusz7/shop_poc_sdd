package com.project.custom.payment.domain;

/**
 * A webhook call whose signature could not be verified. The message never contains the body or the header.
 */
public class InvalidEventSignature extends RuntimeException {

    public InvalidEventSignature(String reason) {
        super(reason);
    }
}

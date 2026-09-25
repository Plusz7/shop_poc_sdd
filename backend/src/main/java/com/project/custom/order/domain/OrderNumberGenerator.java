package com.project.custom.order.domain;

import java.security.SecureRandom;
import java.util.Objects;

/**
 * Generates order numbers from a cryptographically strong random source, so that they cannot be guessed
 * (R-15): 10 Crockford Base32 characters = 50 random bits.
 */
public class OrderNumberGenerator {

    private static final char[] CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int LENGTH = 10;

    private final SecureRandom random;

    public OrderNumberGenerator(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public OrderNumber next() {
        StringBuilder value = new StringBuilder("ORD-");
        for (int i = 0; i < LENGTH; i++) {
            value.append(CROCKFORD_BASE32[random.nextInt(CROCKFORD_BASE32.length)]);
        }
        return new OrderNumber(value.toString());
    }
}

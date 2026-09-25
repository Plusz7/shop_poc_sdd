package com.project.custom.order.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Public, unpredictable order number: {@code ORD-} + 10 Crockford Base32 characters (R-15).
 */
public record OrderNumber(String value) {

    public static final String PATTERN = "^ORD-[0-9A-HJKMNP-TV-Z]{10}$";

    private static final Pattern FORMAT = Pattern.compile(PATTERN);

    public OrderNumber {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Order number has an invalid format");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}

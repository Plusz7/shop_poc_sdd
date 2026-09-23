package com.project.custom.shared.domain;

import java.util.Currency;
import java.util.Objects;

/**
 * An amount of money in minor units (grosze). The shop sells only in PLN; floating point types are
 * never used for money (research R-07).
 */
public record Money(long minor, Currency currency) {

    public static final Currency PLN = Currency.getInstance("PLN");
    public static final Money ZERO = new Money(0, PLN);

    public Money {
        Objects.requireNonNull(currency, "currency");
        if (minor < 0) {
            throw new IllegalArgumentException("Money amount must not be negative");
        }
        if (!PLN.equals(currency)) {
            throw new IllegalArgumentException("Only PLN is supported");
        }
    }

    public static Money pln(long minor) {
        return new Money(minor, PLN);
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(minor, other.minor), currency);
    }

    public Money times(int multiplier) {
        if (multiplier < 0) {
            throw new IllegalArgumentException("Multiplier must not be negative");
        }
        return new Money(Math.multiplyExact(minor, multiplier), currency);
    }
}

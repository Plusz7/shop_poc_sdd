package com.project.custom.order.domain;

import java.util.regex.Pattern;

/**
 * Delivery address; the shop delivers only within Poland (FR-015).
 */
public record ShippingAddress(String streetAndNumber, String postalCode, String city, String country) {

    public static final String POLAND = "PL";

    private static final Pattern POSTAL_CODE = Pattern.compile("^\\d{2}-\\d{3}$");

    public ShippingAddress {
        streetAndNumber = DomainText.stripped(streetAndNumber, "streetAndNumber");
        postalCode = DomainText.stripped(postalCode, "postalCode");
        city = DomainText.stripped(city, "city");
        DomainText.requireLength(streetAndNumber, 3, 120, "streetAndNumber");
        DomainText.requireLength(city, 2, 60, "city");
        if (!POSTAL_CODE.matcher(postalCode).matches()) {
            throw new IllegalArgumentException("postalCode must have the format NN-NNN");
        }
        if (!POLAND.equals(country)) {
            throw new IllegalArgumentException("Only addresses in Poland are supported");
        }
    }

    public static ShippingAddress inPoland(String streetAndNumber, String postalCode, String city) {
        return new ShippingAddress(streetAndNumber, postalCode, city, POLAND);
    }
}

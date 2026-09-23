package com.project.custom.shared.domain;

/**
 * Masks personal data before it reaches a log (research R-20). Pure functions, no state.
 * Secrets (keys) are never logged at all, so they have no masking function here.
 */
public final class PiiMasking {

    private static final String MASK = "***";

    private PiiMasking() {
    }

    /** {@code darek@gmail.com} → {@code d***@g***.com}. */
    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return MASK;
        }
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return text(email);
        }
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        int lastDot = domain.lastIndexOf('.');
        String domainName = lastDot > 0 ? domain.substring(0, lastDot) : domain;
        String topLevel = lastDot > 0 ? domain.substring(lastDot) : "";
        return text(local) + "@" + text(domainName) + topLevel;
    }

    /** Shortens an address to the first letter of each part: {@code M***, 00-***, W***}. */
    public static String address(String streetAndNumber, String postalCode, String city) {
        String postal = postalCode == null || postalCode.length() < 2 ? MASK : postalCode.substring(0, 2) + "-" + MASK;
        return text(streetAndNumber) + ", " + postal + ", " + text(city);
    }

    /** Keeps only the first character: {@code Jan Kowalski} → {@code J***}. */
    public static String text(String value) {
        if (value == null || value.isBlank()) {
            return MASK;
        }
        return value.strip().charAt(0) + MASK;
    }
}

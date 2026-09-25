package com.project.custom.order.domain;

/**
 * Text checks shared by the order value objects.
 */
final class DomainText {

    private DomainText() {
    }

    static String stripped(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.strip();
    }

    /** Length in characters (code points), so that Polish letters count as one. */
    static void requireLength(String value, int min, int max, String name) {
        int length = value.codePointCount(0, value.length());
        if (length < min || length > max) {
            throw new IllegalArgumentException(name + " must have " + min + "-" + max + " characters");
        }
    }
}

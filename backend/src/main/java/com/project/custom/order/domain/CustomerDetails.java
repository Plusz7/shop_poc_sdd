package com.project.custom.order.domain;

import java.util.regex.Pattern;

/**
 * Who placed the order (FR-015). Values are trimmed; the email is checked for a plausible shape only.
 */
public record CustomerDetails(String email, String fullName) {

    private static final int EMAIL_MAX_LENGTH = 254;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public CustomerDetails {
        email = DomainText.stripped(email, "email");
        fullName = DomainText.stripped(fullName, "fullName");
        if (email.length() > EMAIL_MAX_LENGTH || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Email is invalid");
        }
        DomainText.requireLength(fullName, 2, 100, "fullName");
    }
}

package com.project.custom.shared.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier of an anonymous shopper, carried in the {@code shop_guest} cookie (research R-08).
 */
public record GuestId(UUID value) {

    public GuestId {
        Objects.requireNonNull(value, "value");
    }

    public static GuestId random() {
        return new GuestId(UUID.randomUUID());
    }

    /**
     * @throws IllegalArgumentException when the text is not a UUID
     */
    public static GuestId fromString(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Guest id must not be empty");
        }
        UUID uuid = UUID.fromString(text.trim());
        if (!uuid.toString().equalsIgnoreCase(text.trim())) {
            throw new IllegalArgumentException("Guest id is not a canonical UUID");
        }
        return new GuestId(uuid);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

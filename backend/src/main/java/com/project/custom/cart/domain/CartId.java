package com.project.custom.cart.domain;

import java.util.Objects;
import java.util.UUID;

public record CartId(UUID value) {

    public CartId {
        Objects.requireNonNull(value, "value");
    }

    public static CartId random() {
        return new CartId(UUID.randomUUID());
    }
}

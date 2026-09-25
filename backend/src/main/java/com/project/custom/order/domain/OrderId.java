package com.project.custom.order.domain;

import java.util.Objects;
import java.util.UUID;

public record OrderId(UUID value) {

    public OrderId {
        Objects.requireNonNull(value, "value");
    }

    public static OrderId random() {
        return new OrderId(UUID.randomUUID());
    }
}

package com.project.custom.catalog.domain;

public record ProductId(long value) {

    public ProductId {
        if (value <= 0) {
            throw new IllegalArgumentException("Product id must be positive");
        }
    }
}

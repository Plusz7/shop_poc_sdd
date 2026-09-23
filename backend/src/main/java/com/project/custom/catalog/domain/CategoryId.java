package com.project.custom.catalog.domain;

public record CategoryId(long value) {

    public CategoryId {
        if (value <= 0) {
            throw new IllegalArgumentException("Category id must be positive");
        }
    }
}

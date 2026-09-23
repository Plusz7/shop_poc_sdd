package com.project.custom.catalog.domain;

import java.util.Objects;

/**
 * A product image; the main image has {@code displayOrder = 0}.
 */
public record ProductImage(String url, String alt, int displayOrder) {

    public ProductImage {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(alt, "alt");
        if (displayOrder < 0) {
            throw new IllegalArgumentException("Image display order must not be negative");
        }
    }
}

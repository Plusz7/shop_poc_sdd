package com.project.custom.catalog.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A flat catalog category (no subcategories). The slug identifies the category in URLs.
 */
public record Category(CategoryId id, String name, String slug, int displayOrder) {

    public static final Pattern SLUG = Pattern.compile("[a-z0-9-]{1,80}");

    public Category {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(slug, "slug");
        if (!SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException("Category slug must match " + SLUG.pattern());
        }
    }
}

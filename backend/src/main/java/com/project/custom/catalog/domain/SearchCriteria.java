package com.project.custom.catalog.domain;

import java.util.Optional;

/**
 * Normalized product list criteria (FR-001–FR-004, research R-05/R-06). The search phrase is trimmed,
 * cut to {@value #MAX_QUERY_LENGTH} characters and turned into a {@code LIKE ... ESCAPE '\'} pattern
 * with the wildcards taken literally; a reversed price range is swapped.
 */
public final class SearchCriteria {

    public static final int DEFAULT_SIZE = 24;
    public static final int MAX_SIZE = 48;
    public static final int MAX_QUERY_LENGTH = 100;

    private final String categorySlug;
    private final String query;
    private final Long minPriceMinor;
    private final Long maxPriceMinor;
    private final ProductSort sort;
    private final int page;
    private final int size;

    private SearchCriteria(Builder builder) {
        if (builder.page < 0) {
            throw new IllegalArgumentException("Page must not be negative");
        }
        if (builder.size < 1 || builder.size > MAX_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_SIZE);
        }
        if (isNegative(builder.minPriceMinor) || isNegative(builder.maxPriceMinor)) {
            throw new IllegalArgumentException("Price filter must not be negative");
        }
        boolean reversed = builder.minPriceMinor != null && builder.maxPriceMinor != null
                && builder.minPriceMinor > builder.maxPriceMinor;
        this.minPriceMinor = reversed ? builder.maxPriceMinor : builder.minPriceMinor;
        this.maxPriceMinor = reversed ? builder.minPriceMinor : builder.maxPriceMinor;
        this.categorySlug = blankToNull(builder.categorySlug);
        this.query = normalizeQuery(builder.query);
        this.sort = builder.sort == null ? ProductSort.NAME : builder.sort;
        this.page = builder.page;
        this.size = builder.size;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<String> categorySlug() {
        return Optional.ofNullable(categorySlug);
    }

    public Optional<String> query() {
        return Optional.ofNullable(query);
    }

    /** {@code %phrase%} with {@code \ % _ [} escaped by a backslash. */
    public Optional<String> likePattern() {
        return query().map(phrase -> "%" + escapeLike(phrase) + "%");
    }

    public Optional<Long> minPriceMinor() {
        return Optional.ofNullable(minPriceMinor);
    }

    public Optional<Long> maxPriceMinor() {
        return Optional.ofNullable(maxPriceMinor);
    }

    public ProductSort sort() {
        return sort;
    }

    public int page() {
        return page;
    }

    public int size() {
        return size;
    }

    public long offset() {
        return (long) page * size;
    }

    private static String normalizeQuery(String raw) {
        String trimmed = blankToNull(raw);
        if (trimmed == null) {
            return null;
        }
        return trimmed.length() > MAX_QUERY_LENGTH ? trimmed.substring(0, MAX_QUERY_LENGTH) : trimmed;
    }

    private static String escapeLike(String phrase) {
        StringBuilder escaped = new StringBuilder(phrase.length() + 8);
        for (char character : phrase.toCharArray()) {
            if (character == '\\' || character == '%' || character == '_' || character == '[') {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        return escaped.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isNegative(Long value) {
        return value != null && value < 0;
    }

    public static final class Builder {

        private String categorySlug;
        private String query;
        private Long minPriceMinor;
        private Long maxPriceMinor;
        private ProductSort sort;
        private int page;
        private int size = DEFAULT_SIZE;

        private Builder() {
        }

        public Builder categorySlug(String categorySlug) {
            this.categorySlug = categorySlug;
            return this;
        }

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder minPriceMinor(Long minPriceMinor) {
            this.minPriceMinor = minPriceMinor;
            return this;
        }

        public Builder maxPriceMinor(Long maxPriceMinor) {
            this.maxPriceMinor = maxPriceMinor;
            return this;
        }

        public Builder sort(ProductSort sort) {
            this.sort = sort;
            return this;
        }

        public Builder page(int page) {
            this.page = page;
            return this;
        }

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public SearchCriteria build() {
            return new SearchCriteria(this);
        }
    }
}

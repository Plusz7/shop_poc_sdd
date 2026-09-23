package com.project.custom.catalog.domain;

/**
 * Availability derived from the active flag and stock (FR-005). The frontend only presents it.
 */
public enum AvailabilityStatus {
    AVAILABLE,
    LOW_STOCK,
    UNAVAILABLE;

    private static final int LOW_STOCK_THRESHOLD = 3;

    static AvailabilityStatus of(boolean active, int stock) {
        if (!active || stock == 0) {
            return UNAVAILABLE;
        }
        return stock <= LOW_STOCK_THRESHOLD ? LOW_STOCK : AVAILABLE;
    }
}

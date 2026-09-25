package com.project.custom.order.application;

/**
 * What differed between the summary the customer confirmed and a fresh pricing (R-14); the value of the
 * {@code kind} metric label (contracts/metrics.md).
 */
public enum MismatchKind {
    /** A unit price changed. */
    PRICE,
    /** A line became unavailable or its quantity exceeds the stock. */
    AVAILABILITY,
    /** The set of products or their quantities differ. */
    CONTENTS
}

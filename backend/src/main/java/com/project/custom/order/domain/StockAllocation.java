package com.project.custom.order.domain;

import java.util.List;

/**
 * Decreases the catalog stock for the paid order lines, all or nothing (R-16).
 */
@FunctionalInterface
public interface StockAllocation {

    /** @return {@code true} when the stock of every line was decreased, {@code false} when nothing changed */
    boolean decrease(List<OrderLine> lines);
}

package com.project.custom.catalog;

import java.util.List;

/**
 * Write API of the catalog for other bounded contexts (order).
 */
public interface CatalogCommandFacade {

    /**
     * Decreases the stock of all lines, all or nothing (R-16), within the caller's transaction. The products
     * are locked in id order until the transaction ends, so concurrent purchases of the last item cannot both
     * succeed.
     *
     * @return {@code INSUFFICIENT_STOCK} (and nothing changed) when any product does not exist or has fewer
     *         items than required
     */
    StockDecreaseResult decreaseStock(List<StockLineDto> lines);
}

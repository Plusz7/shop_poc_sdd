package com.project.custom.catalog;

import java.util.Map;
import java.util.Set;

/**
 * Read API of the catalog for other bounded contexts (cart, order).
 */
public interface CatalogQueryFacade {

    /**
     * Current price and availability of the given products, including inactive ones (they are reported as
     * {@code UNAVAILABLE}). Ids of products that do not exist are absent from the result.
     */
    Map<Long, PricingProductDto> getForPricing(Set<Long> productIds);
}

package com.project.custom.catalog.domain;

import java.util.Optional;

public interface ProductRepository {

    /** Active products matching the criteria, in the criteria's order with the product id as a tie-breaker. */
    ResultPage<ProductListItem> search(SearchCriteria criteria);

    /** The product if it exists and is active. */
    Optional<Product> findActive(ProductId id);
}

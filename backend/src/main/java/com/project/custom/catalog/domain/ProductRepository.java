package com.project.custom.catalog.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProductRepository {

    /** Active products matching the criteria, in the criteria's order with the product id as a tie-breaker. */
    ResultPage<ProductListItem> search(SearchCriteria criteria);

    /** The product if it exists and is active. */
    Optional<Product> findActive(ProductId id);

    /** The existing products among the given ids, active or not, loaded in a single query. */
    List<Product> findAllByIds(Set<ProductId> ids);
}

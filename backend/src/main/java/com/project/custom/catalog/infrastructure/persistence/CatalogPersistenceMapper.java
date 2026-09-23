package com.project.custom.catalog.infrastructure.persistence;

import com.project.custom.catalog.domain.Category;
import com.project.custom.catalog.domain.CategoryId;
import com.project.custom.catalog.domain.Product;
import com.project.custom.catalog.domain.ProductId;
import com.project.custom.catalog.domain.ProductImage;
import com.project.custom.shared.domain.Money;

/**
 * JPA ↔ domain mapping of the catalog BC.
 */
final class CatalogPersistenceMapper {

    private CatalogPersistenceMapper() {
    }

    static Category toDomain(CategoryJpaEntity entity) {
        return new Category(new CategoryId(entity.getId()), entity.getName(), entity.getSlug(),
                entity.getDisplayOrder());
    }

    static Product toDomain(ProductJpaEntity entity) {
        return new Product(
                new ProductId(entity.getId()),
                entity.getName(),
                entity.getDescription(),
                Money.pln(entity.getPriceMinor()),
                new CategoryId(entity.getCategoryId()),
                entity.getStock(),
                entity.isActive(),
                entity.getImages().stream().map(CatalogPersistenceMapper::toDomain).toList());
    }

    static ProductImage toDomain(ProductImageJpaEntity entity) {
        return new ProductImage(entity.getUrl(), entity.getAlt(), entity.getDisplayOrder());
    }
}

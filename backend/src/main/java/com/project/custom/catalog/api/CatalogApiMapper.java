package com.project.custom.catalog.api;

import com.project.custom.catalog.application.CatalogService.ProductDetails;
import com.project.custom.catalog.domain.AvailabilityStatus;
import com.project.custom.catalog.domain.Category;
import com.project.custom.catalog.domain.Product;
import com.project.custom.catalog.domain.ProductImage;
import com.project.custom.catalog.domain.ProductListItem;
import com.project.custom.catalog.domain.ProductSort;
import com.project.custom.catalog.domain.ResultPage;

import java.util.List;
import java.util.Map;

/**
 * Mapping between the catalog domain and the contract schemas {@code Category}, {@code ProductSummary},
 * {@code ProductSearchResult} and {@code ProductDetails} (openapi.yaml).
 */
final class CatalogApiMapper {

    static final String SORT_VALUES = "name_asc|price_asc|price_desc";

    private static final Map<String, ProductSort> SORTS = Map.of(
            "name_asc", ProductSort.NAME,
            "price_asc", ProductSort.PRICE_ASC,
            "price_desc", ProductSort.PRICE_DESC);

    private CatalogApiMapper() {
    }

    static ProductSort sort(String value) {
        ProductSort sort = SORTS.get(value);
        if (sort == null) {
            throw new IllegalArgumentException("Unsupported sort order");
        }
        return sort;
    }

    static CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.slug(), category.name());
    }

    static ProductSearchResultResponse toResponse(ResultPage<ProductListItem> page) {
        return new ProductSearchResultResponse(
                page.items().stream().map(CatalogApiMapper::toSummary).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }

    static ProductDetailsResponse toResponse(ProductDetails details) {
        Product product = details.product();
        return new ProductDetailsResponse(
                product.id().value(),
                product.name(),
                product.description(),
                product.price().minor(),
                product.availabilityStatus(),
                product.images().stream().map(CatalogApiMapper::toResponse).toList(),
                toResponse(details.category()),
                product.maxPurchasable());
    }

    private static ProductSummaryResponse toSummary(ProductListItem item) {
        return new ProductSummaryResponse(item.id().value(), item.name(), item.price().minor(),
                item.availabilityStatus(), toResponse(item.mainImage()), item.maxPurchasable());
    }

    private static ImageResponse toResponse(ProductImage image) {
        return new ImageResponse(image.url(), image.alt());
    }

    record CategoryResponse(String slug, String name) {
    }

    record ImageResponse(String url, String alt) {
    }

    record ProductSummaryResponse(long id, String name, long priceMinor, AvailabilityStatus status,
                                  ImageResponse image, int maxAddable) {
    }

    record ProductSearchResultResponse(List<ProductSummaryResponse> products, int page, int size,
                                       long totalElements, int totalPages) {
    }

    record ProductDetailsResponse(long id, String name, String description, long priceMinor,
                                  AvailabilityStatus status, List<ImageResponse> images, CategoryResponse category,
                                  int maxAddable) {
    }
}

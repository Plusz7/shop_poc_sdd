package com.project.custom.catalog.api;

import com.project.custom.catalog.api.CatalogApiMapper.CategoryResponse;
import com.project.custom.catalog.api.CatalogApiMapper.ProductDetailsResponse;
import com.project.custom.catalog.api.CatalogApiMapper.ProductSearchResultResponse;
import com.project.custom.catalog.application.CatalogService;
import com.project.custom.catalog.application.ProductNotFoundException;
import com.project.custom.catalog.domain.ProductId;
import com.project.custom.catalog.domain.SearchCriteria;
import com.project.custom.shared.api.ApiException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
class CatalogController {

    private final CatalogService catalogService;

    CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/categories")
    List<CategoryResponse> listCategories() {
        return catalogService.listCategories().stream().map(CatalogApiMapper::toResponse).toList();
    }

    @GetMapping("/products")
    ProductSearchResultResponse searchProducts(
            @RequestParam(required = false) @Pattern(regexp = "[a-z0-9-]{1,80}") String category,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @Min(0) Long minPrice,
            @RequestParam(required = false) @Min(0) Long maxPrice,
            @RequestParam(defaultValue = "name_asc") @Pattern(regexp = CatalogApiMapper.SORT_VALUES) String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + SearchCriteria.DEFAULT_SIZE) @Min(1) @Max(SearchCriteria.MAX_SIZE)
            int size) {
        SearchCriteria criteria = SearchCriteria.builder()
                .categorySlug(category)
                .query(q)
                .minPriceMinor(minPrice)
                .maxPriceMinor(maxPrice)
                .sort(CatalogApiMapper.sort(sort))
                .page(page)
                .size(size)
                .build();
        return CatalogApiMapper.toResponse(catalogService.search(criteria));
    }

    @GetMapping("/products/{id}")
    ProductDetailsResponse getProduct(@PathVariable long id) {
        if (id <= 0) {
            throw ApiException.notFound();
        }
        try {
            return CatalogApiMapper.toResponse(catalogService.productDetails(new ProductId(id)));
        } catch (ProductNotFoundException exception) {
            throw ApiException.notFound();
        }
    }
}

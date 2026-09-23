package com.project.custom.catalog.application;

import com.project.custom.catalog.domain.Category;
import com.project.custom.catalog.domain.CategoryRepository;
import com.project.custom.catalog.domain.Product;
import com.project.custom.catalog.domain.ProductId;
import com.project.custom.catalog.domain.ProductListItem;
import com.project.custom.catalog.domain.ProductRepository;
import com.project.custom.catalog.domain.ResultPage;
import com.project.custom.catalog.domain.SearchCriteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Catalog browsing use cases (US1): categories, product search and the product page.
 */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    CatalogService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<Category> listCategories() {
        return categoryRepository.findAll();
    }

    public ResultPage<ProductListItem> search(SearchCriteria criteria) {
        return productRepository.search(criteria);
    }

    /**
     * @throws ProductNotFoundException when the product does not exist or is inactive
     */
    public ProductDetails productDetails(ProductId productId) {
        Product product = productRepository.findActive(productId).orElseThrow(ProductNotFoundException::new);
        Category category = categoryRepository.findById(product.categoryId())
                .orElseThrow(() -> new IllegalStateException("Product " + productId.value() + " has no category"));
        return new ProductDetails(product, category);
    }

    public record ProductDetails(Product product, Category category) {
    }
}

package com.project.custom.catalog.application;

import com.project.custom.catalog.CatalogQueryFacade;
import com.project.custom.catalog.PricingProductDto;
import com.project.custom.catalog.domain.Product;
import com.project.custom.catalog.domain.ProductId;
import com.project.custom.catalog.domain.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
class CatalogQueryFacadeImpl implements CatalogQueryFacade {

    private final ProductRepository productRepository;

    CatalogQueryFacadeImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public Map<Long, PricingProductDto> getForPricing(Set<Long> productIds) {
        Set<ProductId> ids = productIds.stream()
                .filter(id -> id != null && id > 0)
                .map(ProductId::new)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return productRepository.findAllByIds(ids).stream()
                .map(CatalogQueryFacadeImpl::toPricingDto)
                .collect(Collectors.toMap(PricingProductDto::productId, Function.identity()));
    }

    private static PricingProductDto toPricingDto(Product product) {
        return new PricingProductDto(
                product.id().value(),
                product.name(),
                product.mainImage().url(),
                product.price().minor(),
                product.stock(),
                product.active(),
                product.availabilityStatus().name(),
                product.maxPurchasable());
    }
}

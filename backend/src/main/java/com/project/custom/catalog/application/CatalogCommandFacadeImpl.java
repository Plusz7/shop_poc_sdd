package com.project.custom.catalog.application;

import com.project.custom.catalog.CatalogCommandFacade;
import com.project.custom.catalog.StockDecreaseResult;
import com.project.custom.catalog.StockLineDto;
import com.project.custom.catalog.domain.Product;
import com.project.custom.catalog.domain.ProductId;
import com.project.custom.catalog.domain.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
class CatalogCommandFacadeImpl implements CatalogCommandFacade {

    private final ProductRepository productRepository;

    CatalogCommandFacadeImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public StockDecreaseResult decreaseStock(List<StockLineDto> lines) {
        Map<ProductId, Integer> required = lines.stream().collect(Collectors.toMap(
                line -> new ProductId(line.productId()), StockLineDto::quantity, Integer::sum));
        if (required.isEmpty()) {
            return StockDecreaseResult.DECREASED;
        }
        Map<ProductId, Product> products = productRepository.lockForUpdate(required.keySet()).stream()
                .collect(Collectors.toMap(Product::id, Function.identity()));
        boolean sufficient = required.entrySet().stream().allMatch(line -> {
            Product product = products.get(line.getKey());
            return product != null && product.canDecreaseStock(line.getValue());
        });
        if (!sufficient) {
            return StockDecreaseResult.INSUFFICIENT_STOCK;
        }
        required.forEach((id, quantity) -> products.get(id).decreaseStock(quantity));
        productRepository.saveAll(List.copyOf(products.values()));
        return StockDecreaseResult.DECREASED;
    }
}

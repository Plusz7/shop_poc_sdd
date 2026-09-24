package com.project.custom.catalog.infrastructure.persistence;

import com.project.custom.catalog.domain.Product;
import com.project.custom.catalog.domain.ProductId;
import com.project.custom.catalog.domain.ProductImage;
import com.project.custom.catalog.domain.ProductListItem;
import com.project.custom.catalog.domain.ProductRepository;
import com.project.custom.catalog.domain.ProductSort;
import com.project.custom.catalog.domain.ResultPage;
import com.project.custom.catalog.domain.SearchCriteria;
import com.project.custom.shared.domain.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Product list search in two queries (count + page) with the main image joined in — no N+1 (R-24).
 * Every value is a bound parameter; the sort order comes only from the {@link ProductSort} allow-list.
 * Case- and accent-insensitivity comes from the {@code Latin1_General_100_CI_AI} collation of {@code name} (R-05).
 */
@Repository
class ProductRepositoryAdapter implements ProductRepository {

    private static final Map<ProductSort, String> ORDER_BY = Map.of(
            ProductSort.NAME, "p.name ASC, p.id ASC",
            ProductSort.PRICE_ASC, "p.priceMinor ASC, p.id ASC",
            ProductSort.PRICE_DESC, "p.priceMinor DESC, p.id ASC");

    private final EntityManager entityManager;
    private final ProductJpaRepository jpaRepository;

    ProductRepositoryAdapter(EntityManager entityManager, ProductJpaRepository jpaRepository) {
        this.entityManager = entityManager;
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ResultPage<ProductListItem> search(SearchCriteria criteria) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        String from = fromAndWhere(criteria, parameters);

        Query count = entityManager.createQuery("SELECT COUNT(p) " + from);
        parameters.forEach(count::setParameter);
        long totalElements = ((Number) count.getSingleResult()).longValue();
        if (totalElements <= criteria.offset()) {
            return new ResultPage<>(List.of(), criteria.page(), criteria.size(), totalElements);
        }

        Query page = entityManager.createQuery(
                "SELECT p.id, p.name, p.priceMinor, p.stock, i.url, i.alt " + from
                        + " ORDER BY " + ORDER_BY.get(criteria.sort()));
        parameters.forEach(page::setParameter);
        page.setFirstResult((int) criteria.offset());
        page.setMaxResults(criteria.size());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = page.getResultList();
        return new ResultPage<>(rows.stream().map(ProductRepositoryAdapter::toListItem).toList(),
                criteria.page(), criteria.size(), totalElements);
    }

    @Override
    public Optional<Product> findActive(ProductId id) {
        return jpaRepository.findByIdAndActiveTrue(id.value()).map(CatalogPersistenceMapper::toDomain);
    }

    @Override
    public List<Product> findAllByIds(Set<ProductId> ids) {
        return jpaRepository.findAllByIdIn(ids.stream().map(ProductId::value).toList()).stream()
                .map(CatalogPersistenceMapper::toDomain)
                .toList();
    }

    private static String fromAndWhere(SearchCriteria criteria, Map<String, Object> parameters) {
        StringBuilder jpql = new StringBuilder("""
                FROM ProductJpaEntity p
                JOIN ProductImageJpaEntity i ON i.productId = p.id AND i.displayOrder = 0""");
        criteria.categorySlug().ifPresent(slug -> {
            jpql.append(" JOIN CategoryJpaEntity c ON c.id = p.categoryId AND c.slug = :slug");
            parameters.put("slug", slug);
        });
        jpql.append(" WHERE p.active = true");
        criteria.likePattern().ifPresent(pattern -> {
            jpql.append(" AND p.name LIKE :pattern ESCAPE '\\'");
            parameters.put("pattern", pattern);
        });
        criteria.minPriceMinor().ifPresent(min -> {
            jpql.append(" AND p.priceMinor >= :minPrice");
            parameters.put("minPrice", min);
        });
        criteria.maxPriceMinor().ifPresent(max -> {
            jpql.append(" AND p.priceMinor <= :maxPrice");
            parameters.put("maxPrice", max);
        });
        return jpql.toString();
    }

    private static ProductListItem toListItem(Object[] row) {
        return new ProductListItem(
                new ProductId((Long) row[0]),
                (String) row[1],
                Money.pln((Long) row[2]),
                (Integer) row[3],
                new ProductImage((String) row[4], (String) row[5], 0));
    }
}

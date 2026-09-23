package com.project.custom.catalog.infrastructure.persistence;

import com.project.custom.catalog.domain.Category;
import com.project.custom.catalog.domain.CategoryId;
import com.project.custom.catalog.domain.CategoryRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class CategoryRepositoryAdapter implements CategoryRepository {

    private final CategoryJpaRepository jpaRepository;

    CategoryRepositoryAdapter(CategoryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<Category> findAll() {
        return jpaRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(CatalogPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Category> findById(CategoryId id) {
        return jpaRepository.findById(id.value()).map(CatalogPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Category> findBySlug(String slug) {
        return jpaRepository.findBySlug(slug).map(CatalogPersistenceMapper::toDomain);
    }
}

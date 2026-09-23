package com.project.custom.catalog.domain;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    /** All categories by display order. */
    List<Category> findAll();

    Optional<Category> findById(CategoryId id);

    Optional<Category> findBySlug(String slug);
}

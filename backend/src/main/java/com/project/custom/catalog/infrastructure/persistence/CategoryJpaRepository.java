package com.project.custom.catalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, Long> {

    List<CategoryJpaEntity> findAllByOrderByDisplayOrderAscIdAsc();

    Optional<CategoryJpaEntity> findBySlug(String slug);
}

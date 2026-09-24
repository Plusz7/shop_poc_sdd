package com.project.custom.catalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, Long> {

    @EntityGraph(attributePaths = "images")
    Optional<ProductJpaEntity> findByIdAndActiveTrue(Long id);

    @EntityGraph(attributePaths = "images")
    List<ProductJpaEntity> findAllByIdIn(Collection<Long> ids);
}

package com.project.custom.order.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {

    @EntityGraph(attributePaths = "lines")
    Optional<OrderJpaEntity> findWithLinesById(UUID id);

    @EntityGraph(attributePaths = "lines")
    Optional<OrderJpaEntity> findByNumber(String number);
}

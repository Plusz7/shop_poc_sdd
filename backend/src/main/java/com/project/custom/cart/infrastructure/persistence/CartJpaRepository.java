package com.project.custom.cart.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface CartJpaRepository extends JpaRepository<CartJpaEntity, UUID> {

    @EntityGraph(attributePaths = "lines")
    Optional<CartJpaEntity> findByGuestId(UUID guestId);
}

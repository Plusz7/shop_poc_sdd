package com.project.custom.cart.infrastructure.persistence;

import com.project.custom.cart.domain.Cart;
import com.project.custom.cart.domain.CartId;
import com.project.custom.cart.domain.CartLine;
import com.project.custom.cart.domain.CartRepository;
import com.project.custom.shared.domain.GuestId;
import com.project.custom.shared.domain.Money;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stores the cart aggregate by synchronising the managed JPA entity with the domain state: lines are updated
 * in place, added or removed (orphan removal). Every change also moves {@code updated_at}, so the cart row
 * version is incremented even when only a line quantity changes — two tabs cannot overwrite each other.
 */
@Repository
class CartRepositoryAdapter implements CartRepository {

    private final CartJpaRepository jpaRepository;

    CartRepositoryAdapter(CartJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Cart> findByGuest(GuestId guestId) {
        return jpaRepository.findByGuestId(guestId.value()).map(CartRepositoryAdapter::toDomain);
    }

    @Override
    public void save(Cart cart) {
        CartJpaEntity entity = jpaRepository.findById(cart.id().value())
                .orElseGet(() -> new CartJpaEntity(cart.id().value(), cart.guestId().value()));
        if (entity.getVersion() != cart.version()) {
            throw new ObjectOptimisticLockingFailureException(CartJpaEntity.class, cart.id().value());
        }
        entity.setUpdatedAt(toOffset(cart.updatedAt()));

        Map<Long, CartLine> domainLines = cart.lines().stream()
                .collect(Collectors.toMap(CartLine::productId, Function.identity()));
        entity.getLines().removeIf(line -> !domainLines.containsKey(line.getProductId()));
        Map<Long, CartLineJpaEntity> storedLines = entity.getLines().stream()
                .collect(Collectors.toMap(CartLineJpaEntity::getProductId, Function.identity()));
        for (CartLine line : cart.lines()) {
            CartLineJpaEntity stored = storedLines.get(line.productId());
            if (stored == null) {
                entity.getLines().add(new CartLineJpaEntity(cart.id().value(), line.productId(), line.quantity(),
                        line.priceWhenAdded().minor(), toOffset(line.addedAt())));
            } else {
                stored.setQuantity(line.quantity());
                stored.setPriceWhenAddedMinor(line.priceWhenAdded().minor());
            }
        }
        jpaRepository.save(entity);
    }

    private static Cart toDomain(CartJpaEntity entity) {
        return Cart.restore(
                new CartId(entity.getId()),
                new GuestId(entity.getGuestId()),
                entity.getLines().stream()
                        .map(line -> new CartLine(line.getProductId(), line.getQuantity(),
                                Money.pln(line.getPriceWhenAddedMinor()), line.getAddedAt().toInstant()))
                        .toList(),
                entity.getUpdatedAt().toInstant(),
                entity.getVersion());
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}

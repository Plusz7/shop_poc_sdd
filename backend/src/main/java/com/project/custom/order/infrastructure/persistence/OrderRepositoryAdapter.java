package com.project.custom.order.infrastructure.persistence;

import com.project.custom.order.domain.CustomerDetails;
import com.project.custom.order.domain.Order;
import com.project.custom.order.domain.OrderId;
import com.project.custom.order.domain.OrderLine;
import com.project.custom.order.domain.OrderNumber;
import com.project.custom.order.domain.OrderRepository;
import com.project.custom.order.domain.OrderStatus;
import com.project.custom.order.domain.ReviewReason;
import com.project.custom.order.domain.ShippingAddress;
import com.project.custom.shared.domain.GuestId;
import com.project.custom.shared.domain.Money;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Stores the order aggregate. The lines and customer details are written once, on creation; later saves change
 * only the lifecycle fields (status, payment time, review reason).
 */
@Repository
class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    OrderRepositoryAdapter(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        return jpaRepository.findWithLinesById(id.value()).map(OrderRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Order> findByNumber(OrderNumber number) {
        return jpaRepository.findByNumber(number.value()).map(OrderRepositoryAdapter::toDomain);
    }

    @Override
    public void save(Order order) {
        OrderJpaEntity entity = jpaRepository.findById(order.id().value()).orElseGet(() -> newEntity(order));
        if (entity.getVersion() != order.version()) {
            throw new ObjectOptimisticLockingFailureException(OrderJpaEntity.class, order.id().value());
        }
        entity.updateLifecycle(order.status().name(),
                order.paidAt().map(OrderRepositoryAdapter::toOffset).orElse(null),
                order.reviewReason().map(ReviewReason::name).orElse(null));
        jpaRepository.save(entity);
    }

    private static OrderJpaEntity newEntity(Order order) {
        CustomerDetails customer = order.customer();
        ShippingAddress address = order.shippingAddress();
        return new OrderJpaEntity(order.id().value(), order.number().value(), order.guestId().value(),
                customer.email(), customer.fullName(), address.streetAndNumber(), address.postalCode(),
                address.city(), address.country(), order.total().minor(), toOffset(order.createdAt()),
                order.lines().stream()
                        .map(line -> new OrderLineJpaEntity(order.id().value(), line.lineNo(), line.productId(),
                                line.name(), line.unitPrice().minor(), line.quantity()))
                        .toList());
    }

    private static Order toDomain(OrderJpaEntity entity) {
        return Order.restore(
                new OrderId(entity.getId()),
                new OrderNumber(entity.getNumber()),
                new GuestId(entity.getGuestId()),
                new CustomerDetails(entity.getEmail(), entity.getFullName()),
                new ShippingAddress(entity.getStreet(), entity.getPostalCode(), entity.getCity(),
                        entity.getCountry()),
                entity.getLines().stream()
                        .map(line -> new OrderLine(line.getLineNo(), line.getProductId(), line.getName(),
                                Money.pln(line.getUnitPriceMinor()), line.getQuantity()))
                        .toList(),
                OrderStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt().toInstant(),
                entity.getPaidAt() == null ? null : entity.getPaidAt().toInstant(),
                entity.getReviewReason() == null ? null : ReviewReason.valueOf(entity.getReviewReason()),
                entity.getVersion());
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}

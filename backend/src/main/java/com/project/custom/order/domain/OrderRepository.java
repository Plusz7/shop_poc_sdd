package com.project.custom.order.domain;

import java.util.Optional;

public interface OrderRepository {

    Optional<Order> findById(OrderId id);

    Optional<Order> findByNumber(OrderNumber number);

    /**
     * Stores the order. Fails with an optimistic locking error when the order was changed concurrently
     * since it was loaded.
     */
    void save(Order order);
}

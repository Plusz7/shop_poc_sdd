package com.project.custom.shared.domain.outbox;

/**
 * Port of the transactional outbox (research R-17): the event is stored in the same database transaction as
 * the aggregate change and dispatched later by the job of the fulfillment feature. Never sends anything itself.
 */
public interface OutboxEventPublisher {

    /**
     * Stores the event in the current transaction.
     *
     * @param type        event type, e.g. {@code OrderPaid}
     * @param aggregateId public identifier of the aggregate, e.g. the order number
     * @param payload     serialized to JSON; must not contain secrets
     * @throws IllegalStateException when called outside a transaction
     */
    void publish(String type, String aggregateId, Object payload);
}

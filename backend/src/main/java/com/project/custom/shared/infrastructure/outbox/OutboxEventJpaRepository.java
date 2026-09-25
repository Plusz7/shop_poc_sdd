package com.project.custom.shared.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

/** Public only for the Outbox gauges in {@code shared.infrastructure.metrics} (research R-30). */
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    /** Unsent events, in one query over the {@code (sent_at, created_at)} index. */
    @Query("""
            SELECT new com.project.custom.shared.infrastructure.outbox.PendingOutbox(COUNT(e), MIN(e.createdAt))
            FROM OutboxEventJpaEntity e WHERE e.sentAt IS NULL""")
    PendingOutbox findPending();
}

package com.project.custom.payment.domain;

import java.time.Instant;

/**
 * Deduplication of provider events by their id (R-12, FR-020).
 */
public interface ProcessedEventRepository {

    /**
     * Records the event as processed in the current transaction. A concurrent registration of the same id
     * waits for the other transaction to finish.
     *
     * @return {@code false} when the event had already been registered (a duplicate delivery)
     */
    boolean register(String eventId, String type, Instant now);
}

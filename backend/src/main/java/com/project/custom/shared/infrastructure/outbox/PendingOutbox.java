package com.project.custom.shared.infrastructure.outbox;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Outbox events not sent yet.
 *
 * @param oldestCreatedAt creation time of the oldest of them; {@code null} when there are none
 */
public record PendingOutbox(Long count, OffsetDateTime oldestCreatedAt) {

    public long pending() {
        return count == null ? 0 : count;
    }

    public Optional<OffsetDateTime> oldest() {
        return Optional.ofNullable(oldestCreatedAt);
    }
}

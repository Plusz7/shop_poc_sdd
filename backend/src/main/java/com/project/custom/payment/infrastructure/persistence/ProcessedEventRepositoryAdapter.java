package com.project.custom.payment.infrastructure.persistence;

import com.project.custom.payment.domain.ProcessedEventRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Registers a Stripe event id in the caller's transaction (R-12). {@code UPDLOCK, HOLDLOCK} on the existence
 * check makes a concurrent delivery of the same event wait until the first transaction ends and then see the
 * row, so exactly one of them processes the event.
 */
@Repository
class ProcessedEventRepositoryAdapter implements ProcessedEventRepository {

    private static final String REGISTER = """
            INSERT INTO processed_stripe_event (event_id, type, processed_at)
            SELECT ?, ?, ?
            WHERE NOT EXISTS (SELECT 1 FROM processed_stripe_event WITH (UPDLOCK, HOLDLOCK) WHERE event_id = ?)""";

    private final JdbcTemplate jdbcTemplate;

    ProcessedEventRepositoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean register(String eventId, String type, Instant now) {
        try {
            return jdbcTemplate.update(REGISTER, eventId, type, now.atOffset(ZoneOffset.UTC), eventId) == 1;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }
}

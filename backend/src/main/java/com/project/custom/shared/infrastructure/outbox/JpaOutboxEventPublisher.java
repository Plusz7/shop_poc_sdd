package com.project.custom.shared.infrastructure.outbox;

import com.project.custom.shared.domain.outbox.OutboxEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Writes outbox events as JSON rows in the caller's transaction ({@code MANDATORY}: an event without the
 * aggregate change it describes must never be stored).
 */
@Component
class JpaOutboxEventPublisher implements OutboxEventPublisher {

    private final OutboxEventJpaRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    JpaOutboxEventPublisher(OutboxEventJpaRepository repository, JsonMapper jsonMapper, Clock clock) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String type, String aggregateId, Object payload) {
        repository.save(new OutboxEventJpaEntity(UUID.randomUUID(), type, aggregateId,
                jsonMapper.writeValueAsString(payload), clock.instant().atOffset(ZoneOffset.UTC)));
    }
}

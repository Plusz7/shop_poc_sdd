package com.project.custom.shared.infrastructure.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Nationalized;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
class OutboxEventJpaEntity {

    @Id
    private UUID id;

    @Nationalized
    @Column(nullable = false, length = 100)
    private String type;

    @Nationalized
    @Column(name = "aggregate_id", nullable = false, length = 50)
    private String aggregateId;

    @Nationalized
    @Column(nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(nullable = false)
    private int attempts;

    protected OutboxEventJpaEntity() {
    }

    OutboxEventJpaEntity(UUID id, String type, String aggregateId, String payload, OffsetDateTime createdAt) {
        this.id = id;
        this.type = type;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.createdAt = createdAt;
    }
}

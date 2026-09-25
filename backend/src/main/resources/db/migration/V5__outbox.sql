-- Shared kernel: transactional outbox (research R-17). Written together with the aggregate change; dispatched
-- by the job of the fulfillment feature, which fills sent_at.

CREATE TABLE outbox_event (
    id           UNIQUEIDENTIFIER  NOT NULL CONSTRAINT pk_outbox_event PRIMARY KEY,
    type         NVARCHAR(100)     NOT NULL,
    aggregate_id NVARCHAR(50)      NOT NULL,
    payload      NVARCHAR(MAX)     NOT NULL,
    created_at   DATETIMEOFFSET(3) NOT NULL,
    sent_at      DATETIMEOFFSET(3) NULL,
    attempts     INT               NOT NULL CONSTRAINT df_outbox_event_attempts DEFAULT 0
);

CREATE INDEX ix_outbox_event_sent_at_created_at ON outbox_event (sent_at, created_at);

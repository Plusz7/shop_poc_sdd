-- BC payment: payment attempts and processed Stripe events (data-model.md, research R-11, R-12).

CREATE TABLE payment (
    id                       UNIQUEIDENTIFIER  NOT NULL CONSTRAINT pk_payment PRIMARY KEY,
    -- No FK: the order belongs to the order BC.
    order_id                 UNIQUEIDENTIFIER  NOT NULL,
    guest_id                 UNIQUEIDENTIFIER  NOT NULL,
    amount_minor             BIGINT            NOT NULL CONSTRAINT ck_payment_amount_positive CHECK (amount_minor > 0),
    stripe_session_id        VARCHAR(255)      NULL,
    stripe_payment_intent_id VARCHAR(255)      NULL,
    payment_url              NVARCHAR(1000)    NULL,
    status                   VARCHAR(20)       NOT NULL,
    created_at               DATETIMEOFFSET(3) NOT NULL,
    confirmed_at             DATETIMEOFFSET(3) NULL,
    -- Optimistic locking: a webhook and a new checkout of the same guest may change a payment concurrently.
    version                  BIGINT            NOT NULL
);

CREATE INDEX ix_payment_order_id ON payment (order_id);
CREATE INDEX ix_payment_guest_id_status ON payment (guest_id, status);
CREATE UNIQUE INDEX uq_payment_stripe_session_id ON payment (stripe_session_id) WHERE stripe_session_id IS NOT NULL;

-- Deduplication of webhook deliveries (R-12, FR-020): registered in the same transaction as the effects.
CREATE TABLE processed_stripe_event (
    event_id     VARCHAR(255)      NOT NULL CONSTRAINT pk_processed_stripe_event PRIMARY KEY,
    type         VARCHAR(100)      NOT NULL,
    processed_at DATETIMEOFFSET(3) NOT NULL
);

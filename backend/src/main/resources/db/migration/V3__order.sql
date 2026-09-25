-- BC order: orders with immutable lines (data-model.md, research R-15). "ORDER" is a reserved word, hence "orders".

CREATE TABLE orders (
    id            UNIQUEIDENTIFIER  NOT NULL CONSTRAINT pk_orders PRIMARY KEY,
    -- ORD- + 10 Crockford Base32 characters from SecureRandom (R-15)
    number        VARCHAR(14)       NOT NULL CONSTRAINT uq_orders_number UNIQUE,
    guest_id      UNIQUEIDENTIFIER  NOT NULL,
    email         NVARCHAR(254)     NOT NULL,
    full_name     NVARCHAR(100)     NOT NULL,
    street        NVARCHAR(120)     NOT NULL,
    postal_code   CHAR(6)           NOT NULL,
    city          NVARCHAR(60)      NOT NULL,
    country       CHAR(2)           NOT NULL,
    total_minor   BIGINT            NOT NULL CONSTRAINT ck_orders_total_positive CHECK (total_minor > 0),
    status        VARCHAR(30)       NOT NULL,
    created_at    DATETIMEOFFSET(3) NOT NULL,
    paid_at       DATETIMEOFFSET(3) NULL,
    review_reason NVARCHAR(200)     NULL,
    version       BIGINT            NOT NULL
);

CREATE INDEX ix_orders_guest_id ON orders (guest_id);

CREATE TABLE order_line (
    order_id         UNIQUEIDENTIFIER NOT NULL CONSTRAINT fk_order_line_orders REFERENCES orders (id),
    line_no          INT              NOT NULL,
    -- No FK: the product belongs to the catalog BC; the name and price are copies (FR-017).
    product_id       BIGINT           NOT NULL,
    name             NVARCHAR(200)    NOT NULL,
    unit_price_minor BIGINT           NOT NULL CONSTRAINT ck_order_line_unit_price_positive CHECK (unit_price_minor > 0),
    quantity         INT              NOT NULL CONSTRAINT ck_order_line_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT pk_order_line PRIMARY KEY (order_id, line_no)
);

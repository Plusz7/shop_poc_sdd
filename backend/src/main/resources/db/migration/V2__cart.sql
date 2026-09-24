-- BC cart: one cart per guest (shop_guest cookie) with at most one line per product (data-model.md, R-08, R-09).

CREATE TABLE cart (
    id         UNIQUEIDENTIFIER  NOT NULL CONSTRAINT pk_cart PRIMARY KEY,
    guest_id   UNIQUEIDENTIFIER  NOT NULL CONSTRAINT uq_cart_guest_id UNIQUE,
    updated_at DATETIMEOFFSET(3) NOT NULL,
    version    BIGINT            NOT NULL
);

CREATE TABLE cart_line (
    cart_id                UNIQUEIDENTIFIER  NOT NULL CONSTRAINT fk_cart_line_cart REFERENCES cart (id) ON DELETE CASCADE,
    -- No FK: the product belongs to the catalog BC; availability is checked through CatalogQueryFacade.
    product_id             BIGINT            NOT NULL,
    quantity               INT               NOT NULL CONSTRAINT ck_cart_line_quantity CHECK (quantity BETWEEN 1 AND 99),
    -- Informational only (price change notice, R-09); never used for pricing (FR-010).
    price_when_added_minor BIGINT            NOT NULL,
    added_at               DATETIMEOFFSET(3) NOT NULL,
    CONSTRAINT pk_cart_line PRIMARY KEY (cart_id, product_id)
);

CREATE INDEX ix_cart_line_cart_id ON cart_line (cart_id);

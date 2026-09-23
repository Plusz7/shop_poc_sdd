-- BC catalog: categories, products and product images (data-model.md, research R-05, R-24).

CREATE TABLE category (
    id            BIGINT IDENTITY(1, 1) NOT NULL CONSTRAINT pk_category PRIMARY KEY,
    name          NVARCHAR(80)          NOT NULL CONSTRAINT uq_category_name UNIQUE,
    slug          VARCHAR(80)           NOT NULL CONSTRAINT uq_category_slug UNIQUE,
    display_order INT                   NOT NULL CONSTRAINT df_category_display_order DEFAULT 0
);

CREATE TABLE product (
    id          BIGINT IDENTITY(1, 1) NOT NULL CONSTRAINT pk_product PRIMARY KEY,
    -- Case- and accent-insensitive collation: "LODZ" matches "łódź" (FR-003, R-05). Not Polish_100_CI_AI:
    -- it treats ł, ó, ź etc. as separate letters of the alphabet, so "LODZ" would not match.
    name        NVARCHAR(200) COLLATE Latin1_General_100_CI_AI NOT NULL,
    description NVARCHAR(4000)        NULL,
    price_minor BIGINT                NOT NULL CONSTRAINT ck_product_price_positive CHECK (price_minor > 0),
    category_id BIGINT                NOT NULL CONSTRAINT fk_product_category REFERENCES category (id),
    stock       INT                   NOT NULL CONSTRAINT ck_product_stock_non_negative CHECK (stock >= 0),
    active      BIT                   NOT NULL,
    version     BIGINT                NOT NULL CONSTRAINT df_product_version DEFAULT 0
);

CREATE TABLE product_image (
    product_id    BIGINT        NOT NULL CONSTRAINT fk_product_image_product REFERENCES product (id),
    display_order INT           NOT NULL CONSTRAINT ck_product_image_order CHECK (display_order >= 0),
    url           NVARCHAR(300) NOT NULL,
    alt           NVARCHAR(200) NOT NULL,
    CONSTRAINT pk_product_image PRIMARY KEY (product_id, display_order)
);

CREATE INDEX ix_product_category_active_price ON product (category_id, active, price_minor);
CREATE INDEX ix_product_active_price ON product (active, price_minor);
CREATE INDEX ix_product_active_name ON product (active, name);

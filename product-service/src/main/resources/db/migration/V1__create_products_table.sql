-- ============================================================
-- V1: Create products table
-- ============================================================

CREATE SEQUENCE IF NOT EXISTS product_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE IF NOT EXISTS products (
    id               BIGINT          NOT NULL DEFAULT nextval('product_sequence'),
    name             VARCHAR(255)    NOT NULL,
    description      TEXT,
    sku              VARCHAR(50)     NOT NULL,
    price            NUMERIC(12, 2)  NOT NULL,
    stock_quantity   INT             NOT NULL DEFAULT 0,
    category         VARCHAR(100)    NOT NULL,
    brand            VARCHAR(100),
    image_url        VARCHAR(500),
    status           VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP       NOT NULL DEFAULT now(),
    version          BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_products            PRIMARY KEY (id),
    CONSTRAINT uq_products_sku        UNIQUE (sku),
    CONSTRAINT chk_products_price     CHECK (price > 0),
    CONSTRAINT chk_products_stock     CHECK (stock_quantity >= 0),
    CONSTRAINT chk_products_status    CHECK (status IN ('ACTIVE','INACTIVE','DISCONTINUED','OUT_OF_STOCK'))
);

-- Indexes
CREATE INDEX idx_product_category ON products (category);
CREATE INDEX idx_product_brand    ON products (brand);
CREATE INDEX idx_product_status   ON products (status);
CREATE INDEX idx_product_price    ON products (price);

-- Auto-update updated_at on every row change
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_products_updated_at
    BEFORE UPDATE ON products
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

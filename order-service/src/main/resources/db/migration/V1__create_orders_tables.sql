-- ============================================================
-- V1: Create orders and order_items tables
-- ============================================================

CREATE SEQUENCE IF NOT EXISTS order_sequence
    START WITH 1000
    INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS order_item_sequence
    START WITH 1
    INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS orders (
    id                   BIGINT          NOT NULL DEFAULT nextval('order_sequence'),
    order_number         VARCHAR(36)     NOT NULL,
    customer_id          VARCHAR(255)    NOT NULL,
    customer_email       VARCHAR(255)    NOT NULL,

    -- Financials
    subtotal             NUMERIC(12, 2)  NOT NULL,
    shipping_cost        NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    tax_amount           NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    total_amount         NUMERIC(12, 2)  NOT NULL,

    -- Status
    status               VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    payment_id           VARCHAR(100),
    payment_status       VARCHAR(30),

    -- Shipping address (embedded)
    shipping_full_name   VARCHAR(255)    NOT NULL,
    shipping_line1       VARCHAR(255)    NOT NULL,
    shipping_line2       VARCHAR(255),
    shipping_city        VARCHAR(100)    NOT NULL,
    shipping_state       VARCHAR(100)    NOT NULL,
    shipping_postal_code VARCHAR(20)     NOT NULL,
    shipping_country     VARCHAR(3)      NOT NULL,
    shipping_phone       VARCHAR(20),

    notes                TEXT,
    created_at           TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP       NOT NULL DEFAULT now(),
    version              BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_orders              PRIMARY KEY (id),
    CONSTRAINT uq_orders_number       UNIQUE (order_number),
    CONSTRAINT chk_orders_subtotal    CHECK (subtotal >= 0),
    CONSTRAINT chk_orders_total       CHECK (total_amount >= 0),
    CONSTRAINT chk_orders_status      CHECK (status IN (
        'PENDING','CONFIRMED','PROCESSING','SHIPPED','DELIVERED','CANCELLED','REFUNDED'
    ))
);

CREATE TABLE IF NOT EXISTS order_items (
    id             BIGINT         NOT NULL DEFAULT nextval('order_item_sequence'),
    order_id       BIGINT         NOT NULL,
    product_sku    VARCHAR(50)    NOT NULL,
    product_name   VARCHAR(255)   NOT NULL,
    quantity       INT            NOT NULL,
    unit_price     NUMERIC(12, 2) NOT NULL,
    line_total     NUMERIC(12, 2) NOT NULL,

    CONSTRAINT pk_order_items         PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order   FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT chk_order_item_qty     CHECK (quantity > 0),
    CONSTRAINT chk_order_item_price   CHECK (unit_price > 0)
);

-- Indexes
CREATE INDEX idx_order_customer   ON orders (customer_id);
CREATE INDEX idx_order_status     ON orders (status);
CREATE INDEX idx_order_number     ON orders (order_number);
CREATE INDEX idx_order_created    ON orders (created_at DESC);
CREATE INDEX idx_order_items_order ON order_items (order_id);

-- Auto-update updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_orders_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

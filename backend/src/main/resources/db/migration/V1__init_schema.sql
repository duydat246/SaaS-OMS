-- ─── SHOPS (Tenants) ────────────────────────────────────────────────────────
CREATE TABLE shops (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(100) NOT NULL UNIQUE,
    platform   VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ─── USERS ──────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id  BIGINT       NOT NULL,
    email      VARCHAR(150) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    full_name  VARCHAR(100),
    role       VARCHAR(20)  NOT NULL DEFAULT 'STAFF',
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_users_shop FOREIGN KEY (tenant_id) REFERENCES shops(id)
);
CREATE INDEX idx_users_tenant ON users(tenant_id);

-- ─── REFRESH TOKENS ─────────────────────────────────────────────────────────
CREATE TABLE refresh_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ─── PRODUCTS ───────────────────────────────────────────────────────────────
CREATE TABLE products (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    category        VARCHAR(100),
    platform_source VARCHAR(20)  DEFAULT 'MANUAL',
    external_id     VARCHAR(100),
    image_url       VARCHAR(500),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_products_shop FOREIGN KEY (tenant_id) REFERENCES shops(id)
);
CREATE INDEX idx_products_tenant ON products(tenant_id);

-- ─── SKUs (Product Variants) ────────────────────────────────────────────────
CREATE TABLE skus (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT        NOT NULL,
    tenant_id   BIGINT        NOT NULL,
    sku_code    VARCHAR(100)  NOT NULL,
    variant     VARCHAR(200),
    price       DECIMAL(12,2) NOT NULL DEFAULT 0,
    stock       INT           NOT NULL DEFAULT 0,
    external_id VARCHAR(100),
    UNIQUE (tenant_id, sku_code),
    CONSTRAINT fk_skus_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_skus_shop    FOREIGN KEY (tenant_id)  REFERENCES shops(id)
);
CREATE INDEX idx_skus_product ON skus(product_id);

-- ─── CUSTOMERS ──────────────────────────────────────────────────────────────
CREATE TABLE customers (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    full_name       VARCHAR(150) NOT NULL,
    phone           VARCHAR(30),
    email           VARCHAR(150),
    address         TEXT,
    platform_source VARCHAR(20)  DEFAULT 'MANUAL',
    external_id     VARCHAR(100),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_customers_shop FOREIGN KEY (tenant_id) REFERENCES shops(id)
);
CREATE INDEX idx_customers_tenant ON customers(tenant_id);

-- ─── ORDERS ─────────────────────────────────────────────────────────────────
CREATE TABLE orders (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id         BIGINT        NOT NULL,
    customer_id       BIGINT,
    platform_source   VARCHAR(20)   NOT NULL DEFAULT 'MANUAL',
    platform_order_id VARCHAR(100),
    status            VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    subtotal          DECIMAL(12,2) NOT NULL DEFAULT 0,
    shipping_fee      DECIMAL(12,2) NOT NULL DEFAULT 0,
    discount          DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_amount      DECIMAL(12,2) NOT NULL DEFAULT 0,
    shipping_address  TEXT,
    notes             TEXT,
    ordered_at        TIMESTAMP     NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, platform_source, platform_order_id),
    CONSTRAINT fk_orders_shop     FOREIGN KEY (tenant_id)   REFERENCES shops(id),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
);
CREATE INDEX idx_orders_tenant        ON orders(tenant_id);
CREATE INDEX idx_orders_tenant_status ON orders(tenant_id, status);
CREATE INDEX idx_orders_created       ON orders(tenant_id, created_at);

-- ─── ORDER ITEMS ────────────────────────────────────────────────────────────
CREATE TABLE order_items (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id     BIGINT        NOT NULL,
    sku_id       BIGINT,
    sku_code     VARCHAR(100),
    product_name VARCHAR(200)  NOT NULL,
    variant      VARCHAR(200),
    quantity     INT           NOT NULL DEFAULT 1,
    unit_price   DECIMAL(12,2) NOT NULL,
    total_price  DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_sku   FOREIGN KEY (sku_id)   REFERENCES skus(id)
);
CREATE INDEX idx_order_items_order ON order_items(order_id);

-- ─── PLATFORM CONNECTIONS ───────────────────────────────────────────────────
CREATE TABLE platform_connections (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id        BIGINT       NOT NULL,
    platform         VARCHAR(20)  NOT NULL,
    shop_name        VARCHAR(200),
    access_token     TEXT,
    refresh_token    TEXT,
    token_expires_at TIMESTAMP    NULL,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, platform),
    CONSTRAINT fk_platform_connections_shop FOREIGN KEY (tenant_id) REFERENCES shops(id)
);

-- ─── PRODUCT IMAGES ─────────────────────────────────────────────────────────
CREATE TABLE product_images (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,
    product_id   BIGINT       NULL,
    storage_key  VARCHAR(255) NOT NULL,
    file_name    VARCHAR(255),
    content_type VARCHAR(100) NOT NULL,
    file_size    BIGINT       NOT NULL DEFAULT 0,
    sort_order   INT          NOT NULL DEFAULT 0,
    is_primary   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE KEY uk_product_images_storage_key (storage_key),
    CONSTRAINT fk_product_images_shop    FOREIGN KEY (tenant_id)  REFERENCES shops(id),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);
CREATE INDEX idx_product_images_product ON product_images(product_id);
CREATE INDEX idx_product_images_tenant  ON product_images(tenant_id, product_id);

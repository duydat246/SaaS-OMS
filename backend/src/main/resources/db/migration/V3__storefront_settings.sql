-- ─── TRANG BÁN HÀNG CÔNG KHAI ───────────────────────────────────────────────
-- Mặc định tắt: shop đang có không bị công khai cho tới khi chủ shop tự bật. Slug giữ nguyên.
ALTER TABLE shops
    ADD COLUMN storefront_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN contact_phone      VARCHAR(20) NULL;

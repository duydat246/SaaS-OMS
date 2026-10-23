# Kế hoạch phát triển module Sản phẩm (SaaS OMS)

> Tài liệu này là nguồn sự thật duy nhất cho đội agent khi build module sản phẩm.
> Mọi work order phải bám đúng tài liệu này. Nếu phát hiện mâu thuẫn, dừng lại và báo người dùng.

## 1. Phạm vi

**Trong phạm vi**
- Backend API CRUD sản phẩm + SKU (module mới `com.saasoms.modules.product`)
- 3 màn hình frontend: danh sách, tạo/sửa, chi tiết
- Dọn dẹp code demo cũ của feature `products`

**Ngoài phạm vi (tuyệt đối không làm trong iteration này)**
- Không sửa bất kỳ file nào trong `backend/src/main/java/com/saasoms/modules/order/**`
- Không sửa `frontend/src/app/features/orders/**`
- Không trừ / hoàn tồn kho khi tạo / huỷ đơn
- Không chọn SKU trong form tạo đơn hàng
- Không upload file ảnh (chỉ nhập URL)
- Không đồng bộ sản phẩm từ TikTok / Shopee
- Không nối "Top sản phẩm" ở dashboard vào dữ liệu thật
- Không tạo migration Flyway mới

## 2. Quyết định đã chốt

| Chủ đề | Chốt |
| --- | --- |
| Phạm vi | Chỉ module sản phẩm |
| Mô hình SKU | Luôn có **>= 1 SKU** cho mỗi sản phẩm. Sản phẩm không biến thể = 1 SKU mặc định. |
| Ảnh | Nhập URL + preview, dùng cột `products.image_url` |
| Xoá | **Chỉ xoá mềm (archive)**: `status = 'ARCHIVED'`, không bao giờ xoá dòng |
| Trạng thái | `ACTIVE` (Đang bán) · `INACTIVE` (Ngừng bán) · `ARCHIVED` (Đã xoá) |
| Danh mục | Free text, dropdown gợi ý lấy từ API `categories` |
| Migration | Không cần, schema `V1__init_schema.sql` đã đủ |

## 3. Schema hiện có (không đổi)

```sql
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
```

Lưu ý: `products` **không có** cột giá / tồn kho. Giá và tồn kho nằm ở `skus`.

## 4. Hợp đồng API

Tất cả response bọc trong `ApiResponse<T>`, phân trang dùng `PageResponse<T>`.

| Method | Path | Mô tả |
| --- | --- | --- |
| GET | `/api/products` | Danh sách, tham số: `search`, `category`, `status`, `platformSource`, `page`, `size`, `sort` |
| GET | `/api/products/stats` | `totalProducts`, `activeProducts`, `inactiveProducts`, `outOfStockCount`, `totalStock` |
| GET | `/api/products/categories` | Mảng chuỗi danh mục distinct của tenant |
| GET | `/api/products/{id}` | Chi tiết + `skus[]` |
| POST | `/api/products` | Tạo sản phẩm + SKU, trả 201 |
| PUT | `/api/products/{id}` | Cập nhật sản phẩm + SKU |
| PATCH | `/api/products/{id}/status` | Đổi trạng thái, **chỉ nhận `ACTIVE` hoặc `INACTIVE`** |
| DELETE | `/api/products/{id}` | Xoá mềm: `status = ARCHIVED` |
| POST | `/api/products/{id}/restore` | Khôi phục: `status = ACTIVE` |

### Quy tắc lọc danh sách

- `status` để trống → **loại bỏ `ARCHIVED`** (chỉ trả `ACTIVE` + `INACTIVE`)
- `status = ARCHIVED` → chỉ trả sản phẩm đã xoá
- `search` khớp **tên sản phẩm HOẶC danh mục HOẶC mã SKU** (không phân biệt hoa thường)
- `size` kẹp trong khoảng **1..100**
- `sort` chỉ nhận whitelist: `name`, `createdAt`, `updatedAt`; mặc định `updatedAt,desc`

### Shape `ProductResponse`

```
id, name, description, category, platformSource, externalId, imageUrl,
status, createdAt, updatedAt,
skuCount, minPrice, maxPrice, totalStock,
skus: [{ id, skuCode, variant, price, stock, externalId }]
```

## 5. Cấu trúc file đích

### Backend

```
backend/src/main/java/com/saasoms/modules/product/
├── controller/ProductController.java
├── dto/
│   ├── ProductResponse.java
│   ├── ProductRequest.java            (dùng chung cho create + update)
│   ├── SkuRequest.java
│   ├── UpdateProductStatusRequest.java
│   └── ProductStatsResponse.java
├── entity/Product.java
├── entity/Sku.java
├── entity/ProductStatus.java          (enum)
├── repository/ProductRepository.java
├── repository/SkuRepository.java
└── service/ProductService.java
```

### Frontend

```
frontend/src/app/features/products/
├── products.routes.ts                 (sửa: thêm :id/edit)
├── models/product.model.ts            (mới)
├── models/product-status.ts           (mới)
├── constants/product.constants.ts     (mới)
├── services/product.service.ts        (mới)
└── pages/
    ├── product-list/    .ts .html .scss   (thay toàn bộ demo canvas)
    ├── product-form/    .ts .html .scss   (thay shell rỗng)
    └── product-detail/  .ts .html .scss   (thay shell rỗng)
```

### File khác phải sửa

- `frontend/src/app/layout/topbar/topbar.component.ts` → thêm nhánh `/products/:id/edit` → "Sửa sản phẩm", đặt **trước** nhánh regex `/\/products\/\d+/`
- `frontend/src/app/shared/services/excel-export.service.ts` → thêm `exportProducts()`

### File phải xoá

- `frontend/src/app/features/products/pages/product-list/product-list.component.spec.ts`
- `frontend/src/app/features/products/pages/product-form/product-form.component.spec.ts`
- `frontend/src/app/features/products/pages/product-detail/product-detail.component.spec.ts`
- `frontend/src/assets/images/picture.jpg` (818 KB, không file nào tham chiếu)

## 6. Quy ước backend bắt buộc

- Java 21, Spring Boot 3, Lombok, Maven. Package gốc `com.saasoms.modules.product`.
- Entity: `@ManyToOne(fetch = LAZY) @JoinColumn(name = "tenant_id") Shop shop`.
  `Sku` phải set **cả** `product` và `shop`.
- Mọi truy vấn đều scope theo `TenantContext.get()`. Repo luôn có `findByIdAndShopId(...)`.
- Response: `ApiResponse.ok(data)` / `ApiResponse.ok(data, "message")`.
- Phân trang: `PageResponse.of(page)`.
- Lỗi nghiệp vụ: `BusinessException.notFound(...)`, `.badRequest(...)`, `.conflict(...)` — message tiếng Việt.
- Service method ghi dữ liệu phải `@Transactional`.
- Controller: `@RestController`, `@RequestMapping("/api/products")`, `@RequiredArgsConstructor`, `@Tag(name = "Products")`, mỗi endpoint có `@Operation`.
- Validation dùng `jakarta.validation` + `@Valid` trên `@RequestBody`.
- Không dùng `@EntityGraph` gắn collection cùng `Pageable` (gây phân trang trong bộ nhớ).

### Chống N+1 khi list

1. Query page `Product` theo filter.
2. Lấy `List<Long> ids` của page.
3. Một query `skuRepository.findByShopIdAndProductIdIn(tenantId, ids)`.
4. Group theo `productId` rồi map DTO.

## 7. Quy ước frontend bắt buộc

- Angular 18 standalone component, `ChangeDetectionStrategy.OnPush`, signals, `inject()`.
- PrimeNG 17: `p-table` (lazy), `p-paginator` qua template, `p-button`, `p-tag`, `p-dialog`, `p-toast`, `p-card`, `p-inputtext`, `p-dropdown`, `p-inputnumber`, `p-textarea`, `p-image`.
- Alias import: `@core/*`, `@shared/*`, `@features/*`, `@env/*`.
- Toàn bộ nhãn hiển thị bằng **tiếng Việt có dấu**.
- Style: bám file lân cận trong `features/orders` (dấu nháy kép, trailing comma).
- Service: `HttpClient`, map `ApiResponse<T>` → `T` bằng `map(r => r.data)`.
- Không thêm thư viện mới. Không cài test runner.
- Type `TagSeverity` khai báo cục bộ trong file constants (giống `order.constants.ts`).

### Nhãn trạng thái

| Mã | Nhãn | Severity |
| --- | --- | --- |
| `ACTIVE` | Đang bán | `success` |
| `INACTIVE` | Ngừng bán | `warning` |
| `ARCHIVED` | Đã xoá | `danger` |

## 8. Các bước thực thi

### B1 — Backend: entity + repository

Tạo `Product`, `Sku`, enum `ProductStatus`, `ProductRepository`, `SkuRepository`.

Nghiệm thu:
- `ProductRepository.search(tenantId, status, category, platformSource, search, pageable)` trả `Page<Product>`; khi `status == null` thì `status <> 'ARCHIVED'`.
- `ProductRepository.findByIdAndShopId(id, tenantId)`.
- `ProductRepository.findDistinctCategories(tenantId)`.
- `SkuRepository.findByShopIdAndProductIdIn(tenantId, ids)`.
- `SkuRepository.existsByShopIdAndSkuCode(...)`, `findByShopIdAndSkuCode(...)`.
- Compile xanh.

### B2 — Backend: DTO + validation + service

Nghiệm thu:
- `ProductRequest` validate: `name` `@NotBlank @Size(max=200)`, `category` `@Size(max=100)`, `imageUrl` `@Size(max=500)`, `skus` `@NotEmpty @Valid`.
- `SkuRequest`: `skuCode` `@Size(max=100)` (được để trống → tự sinh `SKU-XXXXXXXX`), `variant` `@Size(max=200)`, `price` `@NotNull @DecimalMin("0")`, `stock` `@NotNull @Min(0)`.
- Trùng `skuCode` trong cùng tenant → `BusinessException.conflict` với message nêu rõ mã và gợi ý khôi phục nếu sản phẩm đã xoá.
- `archive(id)` set `ARCHIVED`; `restore(id)` set `ACTIVE`; `updateStatus` chỉ nhận ACTIVE/INACTIVE.
- Update: SKU gửi lên có `id` thì cập nhật, không có `id` thì thêm mới. SKU có trong DB nhưng bị bỏ khỏi form thì xoá — **chỉ khi chưa từng được tham chiếu bởi `order_items.sku_id`**. Nếu đã dùng trong đơn thì trả `409` kèm mã SKU cụ thể và gợi ý giữ lại biến thể.

### B3 — Backend: controller + test

Nghiệm thu:
- 9 endpoint đúng bảng mục 4.
- `size` kẹp 1..100; `sort` whitelist.
- Test (unit test Mockito cho `ProductService` + `MockMvc` standalone cho `ProductController`, **không cần DB**): tenant isolation (shop B không đọc được sản phẩm shop A → 404), tạo 0 SKU → 400, trùng mã SKU → 409, list mặc định không trả ARCHIVED, archive rồi restore hoạt động.

### B4 — Frontend: model + constants + service

Nghiệm thu:
- `Product`, `Sku`, `ProductStatus`, `ProductFilters`, `ProductStats`, `ProductRequest` khớp shape backend.
- `PRODUCT_STATUS_LABEL_VI`, `productStatusLabelVi()`, `PRODUCT_STATUS_OPTIONS`, `PRODUCT_STATUS_SEVERITY`, `PRODUCT_ROWS_PER_PAGE`.
- `ProductService` đủ 9 method, map `r.data`.
- `ng build` xanh.

### B5 — Frontend: màn hình danh sách

Nghiệm thu:
- Bộ lọc: search, danh mục, trạng thái (4 lựa chọn: Tất cả / Đang bán / Ngừng bán / Đã xoá), nền tảng.
- `p-table` lazy + phân trang server-side.
- Cột: Ảnh, Tên (kèm danh mục), Số SKU, Giá (từ–đến), Tồn kho (badge Hết hàng / Sắp hết / Còn), Trạng thái, Cập nhật, Thao tác.
- Có empty state, loading, toast lỗi, nút "Thêm sản phẩm", nút xuất Excel.
- Không còn canvas / `setInterval`.

### B6 — Frontend: màn hình tạo/sửa

Nghiệm thu:
- Reactive Form 2 cột, `FormArray` SKU, thêm/xoá dòng.
- Validate: tên bắt buộc, >= 1 SKU, giá >= 0, tồn >= 0, chặn trùng mã SKU ngay trên form.
- Preview ảnh theo URL.
- Panel tổng hợp live: số SKU, tổng tồn, khoảng giá.
- Chế độ sửa load dữ liệu theo `:id`.

### B7 — Frontend: chi tiết + route + topbar

Nghiệm thu:
- Trang chi tiết: banner + card thông tin + bảng SKU + khối tổng hợp.
- Sản phẩm `ARCHIVED`: banner "đã xoá", chỉ hiện nút Khôi phục.
- Nút Sửa / Ngừng bán / Xoá có dialog xác nhận.
- Thêm route `:id/edit` vào `products.routes.ts`.
- `topbar.component.ts`: nhánh `:id/edit` đặt trước nhánh regex số.

### B8 — Dọn dẹp + QA cuối

Nghiệm thu:
- Xoá 3 file spec hỏng và `picture.jpg`.
- `npm --prefix frontend run build` xanh.
- Backend compile xanh; test (nếu chạy được) xanh.

## 9. Rủi ro đã biết

1. **Bẫy xoá mềm:** `UNIQUE (tenant_id, sku_code)` vẫn áp dụng cho sản phẩm `ARCHIVED`. Tạo sản phẩm mới trùng mã SKU của sản phẩm đã xoá sẽ lỗi. Message phải nêu rõ: mã đang thuộc sản phẩm đã xoá, hãy khôi phục hoặc dùng mã khác.
2. **Không được hard delete** vì `order_items.sku_id` tham chiếu `skus`.
3. `products` không có cột giá/tồn — mọi phép tổng hợp phải tính từ `skus`.
4. Frontend chưa có test runner → không viết test frontend.
5. Bỏ một SKU khỏi form sửa sẽ xoá dòng đó khỏi DB. Phải kiểm tra `order_items.sku_id` trước bằng native query trong `SkuRepository`; nếu đã được dùng trong đơn thì chặn và trả 409 thay vì để FK ném lỗi 500.
6. Môi trường dev không có MySQL sẵn → test backend phải là unit test dùng Mockito, không dùng `@DataJpaTest`/Testcontainers.

# AGENTS.md — Quy ước phát triển SaaS OMS

> Phiên bản 1.0 · Cập nhật 23/10/2026
> Áp dụng cho mọi người và mọi agent làm việc trong repo này.
> Quy ước riêng của từng vai (backend-dev, frontend-dev, reviewer, QA) nằm trong `.opencode/agents/`; file này là chuẩn chung, không thay thế các file đó.

---

## 1. Mục đích và thứ tự ưu tiên nguồn sự thật

Khi có mâu thuẫn, áp dụng theo thứ tự:

1. **Work order / `docs/ke-hoach-*.md`** — hợp đồng của bước đang làm.
2. **`AGENTS.md`** (file này) — quy ước chung toàn dự án.
3. **Code mẫu đang chạy** — module `product` (mới nhất) và `order` (legacy).
4. **Tài liệu phụ** — `frontend/CLAUDE.md`, `frontend/PROMPT_TEMPLATE.md`, báo cáo trong `docs/`.

Nếu work order trái với file này, hoặc tài liệu trái với code: **dừng lại, báo người dùng**, không tự chọn một bên. Không âm thầm làm theo tài liệu đã lỗi thời.

Phạm vi áp dụng: `backend/**`, `frontend/**`, `docs/**`, cấu hình hạ tầng. Không áp dụng cho thư mục sinh tự động (`target/`, `node_modules/`, `dist/`, `.history/`, `.angular/`).

---

## 2. Bản đồ dự án

| Thành phần | Công nghệ |
| --- | --- |
| Backend | Java 21 · Spring Boot 3.3.4 · Spring Data JPA/Hibernate 6 · Spring Security (JWT) · Bean Validation · Lombok · Flyway · MySQL 8 · springdoc-openapi 2.6 |
| Frontend | Angular 18.2 (standalone, signals) · PrimeNG 17.18 · PrimeFlex 3 · RxJS 7.8 · ExcelJS · Chart.js · TypeScript 5.4 strict |
| Hạ tầng | Docker multi-stage · Docker Compose · GitHub Actions · nginx · Cloudflare Tunnel · VPS Ubuntu |

### Backend

```
backend/src/main/java/com/saasoms/
├── common/
│   ├── config/        JwtFilter, JwtService, SecurityConfig, SwaggerConfig
│   ├── exception/     BusinessException, GlobalExceptionHandler
│   ├── response/      ApiResponse<T>, PageResponse<T>
│   ├── tenant/        TenantContext (ThreadLocal)
│   └── util/
└── modules/<module>/
    ├── controller/    REST + @Operation
    ├── dto/           request/response, không trả entity
    ├── entity/        JPA entity + enum
    ├── repository/    Spring Data JPA
    └── service/       nghiệp vụ + @Transactional
```

Module hiện có: `auth`, `user`, `shop`, `customer`, `order` (legacy), `product` (mẫu tham chiếu mới nhất).

### Frontend

```
frontend/src/app/
├── core/       auth (service, guard), interceptors, models dùng chung
├── shared/     services dùng chung (Excel), components dùng chung
├── layout/     shell, sidebar, topbar, styles
└── features/
    ├── auth/
    ├── dashboard/
    ├── orders/    (legacy)
    └── products/  (mẫu tham chiếu mới nhất)
```

Mỗi feature: `*.routes.ts`, `models/`, `constants/`, `services/`, `pages/`.

### Thư mục cấm chạm (trừ khi work order ghi rõ)

- `backend/src/main/java/com/saasoms/modules/order/**`
- `frontend/src/app/features/orders/**`
- `backend/src/main/resources/db/migration/V1__init_schema.sql`
- `docker-compose*.yml`, `frontend/nginx.conf`, `.github/workflows/**`
- `backend/pom.xml`, `frontend/package.json`, `frontend/angular.json`, `frontend/tsconfig*.json`

---

## 3. Nguyên tắc bất biến

1. **Cách ly tenant tuyệt đối.** Mọi đọc/ghi đều lấy `tenantId` từ `TenantContext.get()`. Không bao giờ nhận `tenantId`/`shopId` từ client. Truy cập chéo tenant trả **404**, không trả 403 (tránh lộ sự tồn tại của dữ liệu).
2. **Không hard delete** dữ liệu nghiệp vụ. Sản phẩm dùng soft-delete: `status = 'ARCHIVED'`; đơn hàng dùng `status = 'CANCELLED'`.
3. **Sản phẩm luôn có ≥ 1 SKU.** Sản phẩm không biến thể = một SKU mặc định.
4. **Tiền và tồn kho không nằm trên `products`.** Giá/tồn nằm ở `skus`; mọi tổng hợp tính từ `skus`.
5. **Tiền dùng `BigDecimal`** (`precision = 12, scale = 2`). Không dùng `double`/`float` cho tiền.
6. **Mọi response API bọc `ApiResponse<T>`**; phân trang dùng `PageResponse<T>`.
7. **UI hiển thị tiếng Việt có dấu.** Tên class/biến/package giữ tiếng Anh. Message lỗi trả người dùng cũng bằng tiếng Việt.
8. **Không thêm thư viện, không cài test runner, không thêm script npm** nếu work order không yêu cầu.
9. **Không thêm migration Flyway** trừ khi được giao rõ. Không sửa migration đã chạy.
10. **Không hard-code dữ liệu giả** trong code production (số liệu dashboard, top sản phẩm, danh mục…).
11. **Secret chỉ qua biến môi trường.** Không commit `.env`, không để secret mặc định trong code/compose.
12. **Không commit, không push, không chạy lệnh git ghi** khi chưa được yêu cầu. Không sửa file ngoài danh sách work order.

---

## 4. Quy ước backend

### 4.1 Package & đặt tên

- Package gốc: `com.saasoms.modules.<module>`.
- Class: `PascalCase`; method/biến: `camelCase`; hằng số: `UPPER_SNAKE_CASE`.
- DTO request: `<Entity>Request`; response: `<Entity>Response`; thống kê: `<Entity>StatsResponse`.
- Không đổi tên class/package đã có nếu không được giao.

### 4.2 Entity

```java
@Entity
@Table(name = "products")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Product {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Shop shop;
}
```

- Quan hệ tenant luôn `@ManyToOne(fetch = LAZY)` + `@JoinColumn(name = "tenant_id", nullable = false)`.
- Enum lưu `@Enumerated(EnumType.STRING)` + `@Column(length = ...)`.
- `createdAt`: `@Column(nullable = false, updatable = false)` + `@Builder.Default = LocalDateTime.now()`.
- `updatedAt`: `@UpdateTimestamp`.
- Không dùng `@Data` trên entity (tránh vòng lặp `equals/hashCode`).
- `Sku` phải set **cả** `product` và `shop`.
- Không map entity trực tiếp ra API.

### 4.3 Repository

- Luôn có `Optional<X> findByIdAndShopId(Long id, Long shopId)` cho thao tác theo id.
- Query JPQL dùng tham số `:shopId`; không nối chuỗi SQL.
- Native query chỉ khi bắt buộc (ví dụ kiểm tra `order_items.sku_id`), phải scope tenant trong câu query.
- Không dùng `@EntityGraph` gắn collection cùng `Pageable` (Hibernate phân trang trong bộ nhớ).

### 4.4 Service

- Method ghi: `@Transactional`; method đọc: `@Transactional(readOnly = true)`.
- Lấy tenant qua helper thống nhất, ném `BusinessException.forbidden` nếu thiếu:
  ```java
  Long tenantId = requireTenant();
  ```
- Không trả entity thô; map sang DTO (`ProductResponse.from(...)`).
- Lỗi nghiệp vụ: `BusinessException.notFound/badRequest/conflict/forbidden` với message tiếng Việt nêu rõ giá trị/trường sai. Không ném `RuntimeException` trần, không để lỗi người dùng thành 500.
- Kiểm tra toàn bộ điều kiện **trước khi ghi** (validate → check trùng → check ràng buộc → ghi), tránh nửa chừng rồi rollback với message xấu.
- Cập nhật collection: xác định rõ thêm/sửa/xoá; kiểm tra ràng buộc FK trước khi xoá.
- Không gọi repository trong vòng lặp khi có thể query theo lô.

### 4.5 Controller

```java
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products")
public class ProductController {
    @GetMapping
    @Operation(summary = "Danh sách sản phẩm có lọc và phân trang")
    public ApiResponse<PageResponse<ProductResponse>> list(...) { ... }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductRequest req) { ... }
}
```

- `@Valid` trên mọi `@RequestBody`; `@PathVariable Long id`.
- `@Tag` + `@Operation(summary = "...")` cho mọi endpoint.
- `POST` tạo mới trả 201 (`@ResponseStatus(HttpStatus.CREATED)`).
- Phân trang: `page >= 0`, `size` kẹp **1..100**, mặc định 20.
- `sort` chỉ nhận whitelist (`name`, `createdAt`, `updatedAt`…), mặc định `updatedAt,desc`; sai whitelist thì fallback, không ném 500.
- Không đặt logic nghiệp vụ trong controller.

### 4.6 DTO & validation

- Request body validate đầy đủ:
  - Chuỗi bắt buộc: `@NotBlank` + `@Size(max = ...)`
  - Số: `@NotNull` + `@DecimalMin("0")` / `@Min(0)` / `@Positive`
  - Collection: `@NotEmpty` + `@Valid` từng phần tử
- Message validation bằng tiếng Việt, nêu rõ trường.
- Không expose field nội bộ (tenant, audit, mật khẩu).
- Response chỉ chứa field client cần; không trả `Page` thô.

### 4.7 Lỗi & mã HTTP

| Tình huống | Mã | Cách tạo |
| --- | --- | --- |
| Dữ liệu sai định dạng/validation | 400 | `@Valid` → `GlobalExceptionHandler` |
| Chưa đăng nhập / token hết hạn | 401 | `AuthenticationEntryPoint` (khi bổ sung) |
| Đã đăng nhập nhưng thiếu quyền | 403 | `BusinessException.forbidden` |
| Không tìm thấy / khác tenant | 404 | `BusinessException.notFound` |
| Trùng mã, xung đột trạng thái | 409 | `BusinessException.conflict` |
| Lỗi không lường trước | 500 | `GlobalExceptionHandler` |

Không trả 500 cho lỗi do người dùng. Không trả message kỹ thuật thô ra client.

### 4.8 Chống N+1 (bắt buộc với quan hệ 1-N)

1. Query `Page<Product>` theo filter.
2. Lấy `List<Long> ids` của page.
3. Một query `findByShopIdAndProductIdIn(tenantId, ids)`.
4. Group theo `productId` rồi map DTO.

### 4.9 Thời gian & trạng thái

- Dùng `LocalDateTime`; API trả ISO-8601.
- Trạng thái sản phẩm: `ACTIVE` (Đang bán) · `INACTIVE` (Ngừng bán) · `ARCHIVED` (Đã xoá).
- `PATCH /status` chỉ nhận `ACTIVE | INACTIVE`; chuyển `ARCHIVED` phải qua endpoint xoá/khôi phục.
- Danh sách mặc định **loại `ARCHIVED`**; chỉ trả khi filter `status=ARCHIVED`.

---

## 5. Quy ước frontend

### 5.1 Cấu trúc & import

- Mỗi feature tự chứa `routes`, `models`, `constants`, `services`, `pages`.
- Alias bắt buộc: `@core/*`, `@shared/*`, `@features/*`, `@env/*`. Không dùng đường dẫn tương đối dài giữa các tầng.
- Import lẫn trong cùng thư mục layout có thể tương đối (không có `@layout/*`).

### 5.2 Component

```ts
@Component({
  selector: "app-product-list",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: "./product-list.component.html",
  styleUrls: ["./product-list.component.scss"],
})
export class ProductListComponent implements OnInit {
  private readonly productService = inject(ProductService);
}
```

- Luôn `standalone: true` + `OnPush`.
- Dùng `inject()` thay constructor injection cho dependency mới.
- State bằng `signal()`; giá trị dẫn xuất bằng `computed()`; side effect bằng `effect()`.
- Template dùng control flow mới `@if`, `@for (item of items; track item.id)`, `@switch`. Không dùng `*ngIf/*ngFor` trừ khi PrimeNG bắt buộc.
- Template dài > 40 dòng tách file `.html`; style tách `.scss`. Không để `.css` trùng `.scss`.
- Không viết business logic trong template; không gọi API trực tiếp trong component mà qua service.

### 5.3 Service

- `@Injectable({ providedIn: 'root' })`.
- `HttpClient` + `HttpParams`; tham số phải là `string` (dùng `String(...)` nếu cần).
- Map wrapper: `.pipe(map(r => r.data))`.
- Trả `Observable<T>`, không subscribe trong service.
- Endpoint base đặt một chỗ: `private readonly base = '/api/products'`.
- Lỗi để component quyết định hiển thị (toast/dialog); không nuốt lỗi im lặng.

### 5.4 Model & constants

- Model khớp 1-1 với shape backend; dùng union type thay `string` cho trạng thái.
- Nhãn tiếng Việt + severity + options đặt trong `constants/` hoặc `models/<status>.ts`, không rải trong template.
- `TagSeverity` khai báo union:
  `"success" | "info" | "secondary" | "contrast" | "warning" | "danger"`.
- Không dùng `as any`. Nếu type chưa khớp, sửa model/type đúng.

### 5.5 PrimeNG

- Import module lẻ (`primeng/table`, `primeng/button`…), không import cả thư viện.
- `p-table` cho danh sách lớn: `[lazy]="true"`, `[paginator]="true"`, `(onPage)` hoặc `(onLazyLoad)`, `[totalRecords]`.
- `[severity]` chỉ nhận union hợp lệ; `[style]` nhận object, không nhận string.
- Có `loading`, empty state (`emptymessage`), và thông báo lỗi rõ ràng.
- Hành động nguy hiểm (xoá, ngừng bán, khôi phục) phải có xác nhận.

### 5.6 Bất đồng bộ & vòng đời

- Subscription trong component phải được huỷ: `takeUntilDestroyed()` hoặc `DestroyRef`.
- Không `setInterval`/`setTimeout`/`requestAnimationFrame` mà không clear trong `ngOnDestroy`.
- Không memory leak: unsubscribe, clear timer, huỷ observer.

### 5.7 Định dạng hiển thị

- Tiền: `Intl.NumberFormat("vi-VN")` hoặc `currency` pipe với `VND` (không có phần thập phân).
- Ngày: `dd/MM/yyyy HH:mm`; ngày ngắn trong bảng: `dd/MM HH:mm`.
- Giá trị rỗng: `—`. Không hiển thị `null`, `undefined`, `NaN`.
- Nhãn nút/hành động: động từ tiếng Việt rõ nghĩa ("Thêm sản phẩm", "Lưu thay đổi", "Khôi phục").

### 5.8 Routing & layout

- Route feature tách file `<feature>.routes.ts`, lazy-load qua `loadComponent`/`loadChildren`.
- Route cụ thể đặt trước route tham số (`new`, `:id/edit` trước `:id`).
- Khi thêm route mới: cập nhật `layout/topbar/topbar.component.ts` (`setTitleFromUrl`) và sidebar nếu là mục menu mới.
- Không phá route hiện có; chỉ thêm.

### 5.9 Accessibility & responsive

- Nút chỉ có icon phải có `aria-label` hoặc `pTooltip`.
- Bảng phải có header rõ; form phải có label (không chỉ placeholder).
- Không hard-code chiều rộng cố định gây tràn mobile; ưu tiên PrimeFlex + media query.

### 5.10 Điều cấm frontend

- Không `any` ngầm định/`as any`, không biến/import thừa, không `console.log` sót.
- Không fake data, không số liệu hardcode trong component production.
- Không dùng `::ng-deep` nếu có cách khác.
- Không thêm dependency, không đổi `angular.json`/`package.json` khi không được giao.

---

## 6. Hợp đồng API

- Prefix: `/api/<resource>` cho nghiệp vụ; `/auth/*` cho xác thực; `/webhooks/*` cho tích hợp.
- Response thành công: `{ "success": true, "data": ..., "message": "..." }`.
- Response lỗi: `{ "success": false, "data": null, "message": "..." }`.
- Danh sách: `PageResponse { content, meta: { page, size, totalElements, totalPages } }`.
- `page` bắt đầu từ 0.
- Không đổi shape response/field đã có mà không thông báo; nếu buộc phải đổi, cập nhật model frontend cùng lúc.
- Tên field JSON: `camelCase`.
- Swagger: mỗi endpoint có `@Operation`; nhóm theo `@Tag`.

---

## 7. Database & Flyway

- Schema quản lý bằng Flyway; `spring.jpa.hibernate.ddl-auto = validate`.
- Không sửa `V1__init_schema.sql`; migration mới đặt tên `V<n>__<mô_tả>.sql` và phải chạy được trên DB đang có dữ liệu.
- Không xoá bảng/cột, không đổi kiểu dữ liệu phá vỡ dữ liệu cũ nếu chưa có kế hoạch.
- Bảng nghiệp vụ phải có `tenant_id` + FK tới `shops`, trừ bảng hệ thống.
- Unique constraint phải tính cả dữ liệu soft-delete (ví dụ `UNIQUE (tenant_id, sku_code)`).
- Không lưu tiền bằng `FLOAT/DOUBLE`; dùng `DECIMAL(12,2)`.
- Không bật `ddl-auto=update` ở bất kỳ profile nào.

---

## 8. Bảo mật

- Mọi endpoint `/api/**` yêu cầu xác thực; chỉ `/auth/**`, `/webhooks/**`, tài liệu Swagger (theo profile) là public.
- `TenantContext` chỉ được set từ JWT trong `JwtFilter`; luôn `clear()` trong `finally`.
- Role hiện có: `OWNER`, `STAFF`. Phân quyền bằng `@PreAuthorize` khi hành động nguy hiểm.
- Không log token, mật khẩu, dữ liệu cá nhân; không bật `show-sql` ở production.
- CORS: allowlist tường minh từ `APP_CORS_ALLOWED_ORIGINS`; không phản chiếu mọi `Origin`.
- Swagger chỉ mở ở dev; production chặn hoặc bảo vệ.
- Bắt buộc validate input ở biên; không tin dữ liệu client.
- Rate-limit bắt buộc cho `/auth/login` và `/auth/register` (xem mục 12).
- Secret (JWT, DB) phải được truyền qua env; không có giá trị mặc định dùng được ở production.

---

## 9. Kiểm thử & cổng chất lượng

### 9.1 Lệnh chuẩn

```powershell
# Backend compile
mvn -q -B -f backend/pom.xml -DskipTests package

# Backend test
mvn -q -B -f backend/pom.xml test

# Frontend build (không có test runner)
npm --prefix frontend run build

# Dev
npm --prefix frontend start        # http://localhost:4200, proxy /api và /auth sang :8080
docker compose up -d               # MySQL 3307, backend 8080, frontend 4200
```

### 9.2 Backend test

- Đặt trong `backend/src/test/java/...`, theo package của class được test.
- Unit test service: Mockito (`@ExtendWith(MockitoExtension.class)`), không cần DB.
- Controller: `MockMvc` standalone.
- Không dùng `@DataJpaTest`/Testcontainers khi môi trường không có MySQL.
- Test bắt buộc cho luồng rủi ro:
  - Cách ly tenant (shop B không đọc/sửa được dữ liệu shop A → 404)
  - Validation (0 SKU → 400, giá/tồn âm → 400)
  - Trùng mã SKU → 409
  - Danh sách mặc định không trả `ARCHIVED`
  - Archive → restore hoạt động
  - Không xoá SKU đã dùng trong đơn → 409

### 9.3 Frontend

- Chưa có test runner → **không thêm**. Cổng chất lượng là `npm run build` xanh + tự kiểm tra UI.
- Không commit file `.spec.ts` rỗng hoặc test giả.

### 9.4 Cổng chất lượng (Definition of Done)

Một bước chỉ được coi là xong khi:

- [ ] `code-reviewer` không còn finding **blocker/major**
- [ ] Build/test tương ứng xanh (chạy thật, không suy đoán)
- [ ] `git status --short` không có file ngoài danh sách work order
- [ ] Không thêm dependency/migration ngoài yêu cầu
- [ ] Nhãn UI tiếng Việt, không còn dữ liệu giả
- [ ] Đã cập nhật tài liệu nếu hành vi API thay đổi

---

## 10. Git & CI

- Nhánh: `main` (production), `dev` (tích hợp). Không push trực tiếp lên `main`.
- Commit theo Conventional Commits: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `build:`, `chore:`.
- Commit nhỏ, một mục đích; message tiếng Anh, mô tả ngắn gọn hành vi.
- Agent **không** commit/push/merge/checkout/reset. Chỉ người dùng thực hiện.
- Trước khi đề nghị commit: chạy build/test, kiểm tra `git diff`, không để file rác (`.history`, `dist`, log).
- CI hiện build Docker cho cả `main` và `dev`, tag `latest`, chưa chạy test — đây là nợ cần xử lý (mục 12), không phải mẫu để nhân rộng.

---

## 11. Hạ tầng & triển khai

- Dev: `docker-compose.yml` (MySQL 3307, backend 8080, frontend 4200).
- Prod: `docker-compose.prod.yml` dùng image Docker Hub; cấu hình qua `.env` trên VPS.
- nginx phục vụ SPA và proxy `/api`, `/auth` tới backend; app và API cùng origin nên **không cần CORS ở nginx**.
- Không sửa `nginx.conf`, compose, workflow khi work order không yêu cầu.
- Khi sửa hạ tầng: giữ nguyên cổng/volume/healthcheck; thêm biến mới phải cập nhật `.env.example`; không commit `.env`.
- Image production phải gắn tag phiên bản (commit SHA) để rollback; không chỉ dùng `latest`.

---

## 12. Nợ kỹ thuật đã biết

> Trạng thái tại 23/10/2026. Quy tắc: **sửa khi chạm** — nếu làm việc trong vùng bị ảnh hưởng, sửa luôn hoặc báo rõ lý do chưa sửa. Không làm ngầm, không nhân rộng pattern sai.

| # | Mức | Vấn đề | Bằng chứng | Hướng xử lý |
| --- | --- | --- | --- | --- |
| 1 | P0 | nginx phản chiếu `Origin` bất kỳ + strip `Origin`, bỏ qua allowlist CORS | `frontend/nginx.conf:10,21,25,43,47` | Bỏ toàn bộ block CORS và `proxy_set_header Origin ""`; app/API cùng origin |
| 2 | P0 | Prod chạy profile `dev` (bật `show-sql`, log DEBUG) | `docker-compose.prod.yml:33`, `application-dev.yml:8,12` | Tạo `application-prod.yml`, đổi `SPRING_PROFILES_ACTIVE=prod` |
| 3 | P0 | Swagger/`/api-docs` mở công khai | `SecurityConfig.java:44` | Chỉ mở ở dev hoặc chặn bằng role |
| 4 | P0 | Secret mặc định trong repo (JWT, MySQL) | `docker-compose.yml:7,8,29`, `application-dev.yml:5` | Bắt buộc env, xoá default dùng được ở prod |
| 5 | P1 | `.gitignore` chặn mọi `*.md` | `.gitignore:7` | Thêm `!AGENTS.md` (đã xử lý cùng file này) |
| 6 | P1 | Login chưa rate-limit; mật khẩu tối thiểu 6 ký tự | `AuthService.java:63`, `RegisterRequest` | Rate-limit theo IP/email, nâng chính sách mật khẩu |
| 7 | P1 | Thiếu 401 entry point → trả 403 khi hết token | `SecurityConfig.java` | Thêm `AuthenticationEntryPoint` trả 401 + `ApiResponse` |
| 8 | P1 | CI deploy cả `dev`, tag `latest`, không chạy test | `.github/workflows/deploy.yml:5,24,31` | Chỉ deploy từ `main`, tag SHA, thêm `mvn test` + `npm build` |
| 9 | P1 | Dashboard dùng số liệu giả; export cắt cứng 1000 dòng | `dashboard.component.ts:106-127`, `dashboard.constants.ts:18`, `:322` | Nối API thật; phân trang khi export và báo số dòng |
| 10 | P2 | Order chưa kẹp `size`, bỏ qua `sort` | `OrderController.java:34-37` | Kẹp 1..100, nhận sort whitelist |
| 11 | P2 | `as any`, subscription không huỷ, `.css` trùng | `product-list.component.ts:100,132`, `product-form.component.ts:203,271,287`, `product.service.ts:75` | Bỏ `any`, dùng `takeUntilDestroyed`, xoá `.css` thừa |
| 12 | P2 | Message lỗi lẫn Anh/Việt | `GlobalExceptionHandler.java:41,46,52`, `OrderService` | Chuẩn hoá message tiếng Việt |
| 13 | P2 | Tài liệu lệch code (localStorage, thiếu route products) | `frontend/CLAUDE.md:34,38-44` | Cập nhật tài liệu khi chạm; code là chuẩn |
| 14 | P2 | `ordered_at` không bao giờ được ghi | `OrderService.createOrder()` | Set khi tạo đơn (khi có work order) |
| 15 | P2 | Tích hợp TikTok/Shopee chưa có dù schema sẵn | `platform_connections` | Để sprint riêng, không tự làm |

---

## 13. Checklist trước khi báo xong

### Backend
- [ ] Đọc lại file vừa viết: import, kiểu, Lombok, naming
- [ ] Mọi query scope tenant; có `findByIdAndShopId`
- [ ] `@Transactional` đúng chỗ; validation đầy đủ
- [ ] Lỗi nghiệp vụ dùng `BusinessException`, message tiếng Việt
- [ ] Không N+1; không trả entity thô
- [ ] `mvn -q -B -f backend/pom.xml -DskipTests package` xanh
- [ ] Test mới (nếu có) chạy xanh

### Frontend
- [ ] Standalone + OnPush + signals/inject
- [ ] Không `any`, không import thừa, không subscription rò
- [ ] Loading/empty/error đầy đủ; nhãn tiếng Việt
- [ ] `[severity]`/`[style]` đúng kiểu
- [ ] Route + topbar + sidebar cập nhật nếu cần
- [ ] `npm --prefix frontend run build` xanh

### Chung
- [ ] Không file ngoài phạm vi; không dependency/migration mới
- [ ] Không fake data, không secret, không `console.log`
- [ ] Báo cáo rõ: file thay đổi, quyết định kỹ thuật, điểm chưa chắc chắn

---

## 14. Bảo trì file này

- Cập nhật `AGENTS.md` khi: thêm module mới, đổi pattern chung, đổi lệnh build, đổi quy ước API/hạ tầng.
- Không lặp lại nội dung đã có trong `.opencode/agents/*.md`; chỉ ghi chuẩn chung.
- Thay đổi quy ước phải được người dùng duyệt; agent không tự nới lỏng rule.
- Khi sửa nợ kỹ thuật ở mục 12: cập nhật trạng thái ngay trong cùng commit.
- Mọi trao đổi với người dùng bằng **tiếng Việt**; định danh kỹ thuật (class, biến, file) giữ tiếng Anh.

import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { ActivatedRoute, ParamMap, Params, Router, RouterLink } from "@angular/router";
import { catchError, debounceTime, finalize, from, map, merge, of, Subject, switchMap, tap } from "rxjs";
import { ConfirmationService, MessageService } from "primeng/api";
import { TableModule, TablePageEvent } from "primeng/table";
import { ButtonModule } from "primeng/button";
import { ConfirmDialogModule } from "primeng/confirmdialog";
import { InputTextModule } from "primeng/inputtext";
import { TagModule } from "primeng/tag";
import { TooltipModule } from "primeng/tooltip";
import { DropdownModule } from "primeng/dropdown";
import { PageMeta } from "@core/models/api.model";
import { ExcelExportService } from "@shared/services/excel-export.service";
import { Product, ProductFilters, ProductStats, ProductStatus } from "@features/products/models/product.model";
import { productStatusLabelVi } from "@features/products/models/product-status";
import { ProductService } from "@features/products/services/product.service";
import { ProductListStateService } from "@features/products/services/product-list-state.service";
import {
  PRODUCT_DEFAULT_PAGE_SIZE,
  PRODUCT_DEFAULT_SORT,
  PRODUCT_PLATFORM_OPTIONS,
  PRODUCT_ROWS_PER_PAGE,
  PRODUCT_SORT_OPTIONS,
  PRODUCT_STATUS_FILTER_OPTIONS,
  PRODUCT_STATUS_SEVERITY,
  ProductOption,
  TagSeverity,
  formatPriceRange,
  formatQuantity,
  productPlatformLabelVi,
  stockLabelVi,
  stockSeverity,
} from "@features/products/constants/product.constants";

const PLACEHOLDER_IMAGE = "assets/images/placeholder.svg";
const SEARCH_DEBOUNCE_MS = 350;

@Component({
  selector: "app-product-list",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    RouterLink,
    FormsModule,
    TableModule,
    ButtonModule,
    ConfirmDialogModule,
    InputTextModule,
    TagModule,
    TooltipModule,
    DropdownModule,
  ],
  providers: [ConfirmationService],
  templateUrl: "./product-list.component.html",
  styleUrls: ["./product-list.component.scss"],
})
export class ProductListComponent {
  private readonly productService = inject(ProductService);
  private readonly listState = inject(ProductListStateService);
  private readonly excelExportService = inject(ExcelExportService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly reload$ = new Subject<void>();
  private readonly searchInput$ = new Subject<void>();

  readonly products = signal<Product[]>([]);
  readonly meta = signal<PageMeta>({
    page: 0,
    size: PRODUCT_DEFAULT_PAGE_SIZE,
    totalElements: 0,
    totalPages: 0,
  });
  readonly stats = signal<ProductStats | null>(null);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly exporting = signal(false);
  /** Id sản phẩm đang xoá/khôi phục, để khoá nút trên đúng dòng đó. */
  readonly busyProductId = signal<number | null>(null);

  readonly searchText = signal("");
  readonly selectedCategory = signal<string | null>(null);
  readonly selectedStatus = signal<ProductStatus | null>(null);
  readonly selectedPlatform = signal<string | null>(null);
  readonly selectedSort = signal(PRODUCT_DEFAULT_SORT);

  readonly categoryOptions = signal<ProductOption[]>([]);
  readonly statusOptions = PRODUCT_STATUS_FILTER_OPTIONS;
  readonly platformOptions = PRODUCT_PLATFORM_OPTIONS;
  readonly sortOptions = PRODUCT_SORT_OPTIONS;
  readonly rowsPerPageOptions = PRODUCT_ROWS_PER_PAGE;

  readonly first = computed(() => this.meta().page * this.meta().size);
  readonly hasActiveFilters = computed(
    () =>
      !!this.searchText().trim() ||
      this.selectedCategory() !== null ||
      this.selectedStatus() !== null ||
      this.selectedPlatform() !== null,
  );

  readonly productStatusLabelVi = productStatusLabelVi;
  readonly platformLabelVi = productPlatformLabelVi;
  readonly stockLabel = stockLabelVi;
  readonly stockSeverity = stockSeverity;
  readonly formatQuantity = formatQuantity;

  constructor() {
    const fromUrl$ = this.route.queryParamMap.pipe(
      tap((params) => {
        this.applyQuery(params);
        this.listState.lastQuery.set(this.toQueryParams(this.meta().page));
      }),
    );

    merge(fromUrl$, this.reload$)
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.loadError.set(null);
        }),
        // switchMap: đổi bộ lọc nhanh thì response cũ không được ghi đè response mới.
        switchMap(() =>
          this.productService.getProducts(this.currentFilters()).pipe(
            map((page) => ({ page, error: null as string | null })),
            catchError((err) =>
              of({
                page: null,
                error: (err?.error?.message as string | undefined) ?? "Không tải được danh sách sản phẩm.",
              }),
            ),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(({ page, error }) => {
        this.loading.set(false);
        if (!page) {
          this.products.set([]);
          this.loadError.set(error);
          return;
        }
        // Xoá dòng cuối của trang cuối, hoặc mở URL cũ: lùi về trang cuối còn dữ liệu.
        if (page.content.length === 0 && page.meta.page > 0 && page.meta.totalPages > 0) {
          this.navigate(page.meta.totalPages - 1, true);
          return;
        }
        if (page.content.length === 0 && page.meta.page > 0) {
          this.navigate(0, true);
          return;
        }
        this.products.set(page.content);
        this.meta.set(page.meta);
      });

    this.searchInput$
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), takeUntilDestroyed())
      .subscribe(() => {
        // Chỉ gõ thêm dấu cách thì từ khoá không đổi: không về trang đầu, không tải lại.
        const urlSearch = this.route.snapshot.queryParamMap.get("search") ?? "";
        if (this.searchText().trim() !== urlSearch.trim()) {
          this.applyFilters();
        }
      });

    this.loadCategories();
    this.loadStats();
  }

  // --- Bộ lọc ------------------------------------------------------------

  onSearchInput(value: string): void {
    this.searchText.set(value);
    this.searchInput$.next();
  }

  clearSearch(): void {
    this.searchText.set("");
    this.applyFilters();
  }

  onCategoryChange(value: string | null): void {
    this.selectedCategory.set(value ?? null);
    this.applyFilters();
  }

  onStatusChange(value: ProductStatus | null): void {
    this.selectedStatus.set(value ?? null);
    this.applyFilters();
  }

  onPlatformChange(value: string | null): void {
    this.selectedPlatform.set(value ?? null);
    this.applyFilters();
  }

  onSortChange(value: string | null): void {
    this.selectedSort.set(value ?? PRODUCT_DEFAULT_SORT);
    this.applyFilters();
  }

  applyFilters(): void {
    this.navigate(0);
  }

  clearFilters(): void {
    this.searchText.set("");
    this.selectedCategory.set(null);
    this.selectedStatus.set(null);
    this.selectedPlatform.set(null);
    this.navigate(0);
  }

  onPage(event: TablePageEvent): void {
    const size = event.rows ?? this.meta().size;
    this.meta.update((meta) => ({ ...meta, size }));
    this.navigate(Math.floor((event.first ?? 0) / size));
  }

  retry(): void {
    this.reload$.next();
  }

  /**
   * Trạng thái lọc nằm trên URL để F5 hoặc quay lại từ trang chi tiết không mất bộ lọc.
   * `replaceUrl`: dùng khi tự lùi trang, nếu không nút Back sẽ về URL rỗng rồi lại bị đẩy đi.
   */
  private navigate(page: number, replaceUrl = false): void {
    const queryParams = this.toQueryParams(page);
    if (this.sameAsCurrentUrl(queryParams)) {
      this.reload$.next();
      return;
    }
    this.router.navigate([], { relativeTo: this.route, queryParams, replaceUrl });
  }

  private sameAsCurrentUrl(queryParams: Params): boolean {
    const current = this.route.snapshot.queryParams;
    const keys = new Set([...Object.keys(current), ...Object.keys(queryParams)]);
    return [...keys].every((key) => String(current[key] ?? "") === String(queryParams[key] ?? ""));
  }

  private toQueryParams(page: number): Params {
    const params: Params = {};
    const search = this.searchText().trim();
    if (search) params["search"] = search;
    if (this.selectedCategory()) params["category"] = this.selectedCategory();
    if (this.selectedStatus()) params["status"] = this.selectedStatus();
    if (this.selectedPlatform()) params["platformSource"] = this.selectedPlatform();
    if (this.selectedSort() !== PRODUCT_DEFAULT_SORT) params["sort"] = this.selectedSort();
    if (page > 0) params["page"] = String(page);
    if (this.meta().size !== PRODUCT_DEFAULT_PAGE_SIZE) params["size"] = String(this.meta().size);
    return params;
  }

  private applyQuery(params: ParamMap): void {
    const status = params.get("status");
    const platform = params.get("platformSource");
    const sort = params.get("sort");
    const page = Number(params.get("page"));
    const size = Number(params.get("size"));

    // URL luôn lưu bản đã trim: cùng nội dung thì giữ nguyên ô nhập để không mất dấu cách đang gõ.
    const search = params.get("search") ?? "";
    if (this.searchText().trim() !== search.trim()) {
      this.searchText.set(search);
    }
    this.selectedCategory.set(params.get("category"));
    this.selectedStatus.set(this.statusOptions.find((o) => o.value === status)?.value ?? null);
    this.selectedPlatform.set(this.platformOptions.find((o) => o.value === platform)?.value ?? null);
    this.selectedSort.set(this.sortOptions.find((o) => o.value === sort)?.value ?? PRODUCT_DEFAULT_SORT);
    this.meta.update((meta) => ({
      ...meta,
      page: Number.isInteger(page) && page > 0 ? page : 0,
      size: this.rowsPerPageOptions.includes(size) ? size : PRODUCT_DEFAULT_PAGE_SIZE,
    }));
  }

  private currentFilters(): ProductFilters {
    return {
      page: this.meta().page,
      size: this.meta().size,
      sort: this.selectedSort(),
      search: this.searchText().trim() || undefined,
      category: this.selectedCategory() ?? undefined,
      status: this.selectedStatus() ?? undefined,
      platformSource: this.selectedPlatform() ?? undefined,
    };
  }

  // --- Dữ liệu phụ -------------------------------------------------------

  private loadCategories(): void {
    this.productService
      .getCategories()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (categories) =>
          this.categoryOptions.set(
            (categories ?? []).filter((c) => c?.trim()).map((c) => ({ label: c, value: c })),
          ),
        // Danh mục chỉ là bộ lọc phụ: lỗi thì để trống, danh sách chính vẫn dùng được.
        error: () => this.categoryOptions.set([]),
      });
  }

  private loadStats(): void {
    this.productService
      .getStats()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (stats) => this.stats.set(stats),
        // Thẻ thống kê hiển thị "—" khi không có số liệu, không hiện số giả.
        error: () => this.stats.set(null),
      });
  }

  statValue(value: number | undefined): string {
    return value === undefined ? "—" : formatQuantity(value);
  }

  // --- Hành động ---------------------------------------------------------

  confirmArchive(product: Product): void {
    this.confirmationService.confirm({
      message: `Xoá sản phẩm "${product.name}"? Bạn có thể khôi phục lại từ bộ lọc "Đã xoá".`,
      header: "Xác nhận xoá",
      icon: "pi pi-exclamation-triangle",
      acceptLabel: "Xoá",
      rejectLabel: "Huỷ",
      acceptButtonStyleClass: "p-button-danger",
      accept: () => this.archive(product),
    });
  }

  private archive(product: Product): void {
    this.busyProductId.set(product.id);
    this.productService
      .archiveProduct(product.id)
      .pipe(
        finalize(() => this.busyProductId.set(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.afterMutation(`Đã xoá sản phẩm "${product.name}".`),
        error: (err) => this.showError(err?.error?.message ?? "Không xoá được sản phẩm."),
      });
  }

  confirmRestore(product: Product): void {
    this.confirmationService.confirm({
      message: `Khôi phục sản phẩm "${product.name}" về trạng thái Đang bán?`,
      header: "Xác nhận khôi phục",
      icon: "pi pi-refresh",
      acceptLabel: "Khôi phục",
      rejectLabel: "Huỷ",
      accept: () => this.restore(product),
    });
  }

  private restore(product: Product): void {
    this.busyProductId.set(product.id);
    this.productService
      .restoreProduct(product.id)
      .pipe(
        finalize(() => this.busyProductId.set(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.afterMutation(`Đã khôi phục sản phẩm "${product.name}".`),
        error: (err) => this.showError(err?.error?.message ?? "Không khôi phục được sản phẩm."),
      });
  }

  private afterMutation(detail: string): void {
    this.messageService.add({ severity: "success", summary: "Thành công", detail });
    this.reload$.next();
    this.loadStats();
    this.loadCategories();
  }

  /** Xuất toàn bộ sản phẩm khớp bộ lọc, không chỉ trang đang xem. */
  exportExcel(): void {
    this.exporting.set(true);
    this.productService
      .getAllProducts(this.currentFilters())
      .pipe(
        switchMap((products) =>
          from(this.excelExportService.exportProducts(products)).pipe(map(() => products.length)),
        ),
        finalize(() => this.exporting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (count) =>
          this.messageService.add({
            severity: "success",
            summary: "Thành công",
            detail: `Đã xuất ${formatQuantity(count)} sản phẩm ra Excel.`,
          }),
        error: (err) => this.showError(err?.error?.message ?? "Không xuất được file Excel."),
      });
  }

  private showError(detail: string): void {
    this.messageService.add({ severity: "error", summary: "Lỗi", detail });
  }

  // --- Hiển thị ----------------------------------------------------------

  goToDetail(id: number): void {
    this.router.navigate(["/products", id]);
  }

  goToEdit(id: number): void {
    this.router.navigate(["/products", id, "edit"]);
  }

  statusSeverity(status: ProductStatus): TagSeverity {
    return PRODUCT_STATUS_SEVERITY[status];
  }

  priceLabel(product: Product): string {
    return formatPriceRange(product.minPrice, product.maxPrice);
  }

  imageSrc(product: Product): string {
    return product.imageUrl || PLACEHOLDER_IMAGE;
  }

  onImageError(event: Event): void {
    const target = event.target as HTMLImageElement;
    // Chặn vòng lặp vô hạn nếu chính ảnh placeholder cũng lỗi.
    if (!target.src.endsWith(PLACEHOLDER_IMAGE)) {
      target.src = PLACEHOLDER_IMAGE;
    }
  }
}

import { Component, ChangeDetectionStrategy, DestroyRef, computed, inject, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { ActivatedRoute, Router, RouterLink } from "@angular/router";
import { MessageService, ConfirmationService } from "primeng/api";
import { CardModule } from "primeng/card";
import { ButtonModule } from "primeng/button";
import { TagModule } from "primeng/tag";
import { TableModule } from "primeng/table";
import { DialogModule } from "primeng/dialog";
import { ConfirmDialogModule } from "primeng/confirmdialog";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { Product, ProductImage } from "@features/products/models/product.model";
import { productStatusLabelVi } from "@features/products/models/product-status";
import {
  LOW_STOCK_THRESHOLD,
  PRODUCT_STATUS_SEVERITY,
  TagSeverity,
  formatPriceRange,
  formatQuantity,
  formatVnd,
  productPlatformLabelVi,
} from "@features/products/constants/product.constants";
import { ProductService } from "@features/products/services/product.service";
import { ProductListStateService } from "@features/products/services/product-list-state.service";

@Component({
  selector: "app-product-detail",
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    CardModule,
    ButtonModule,
    TagModule,
    TableModule,
    DialogModule,
    ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  templateUrl: "./product-detail.component.html",
  styleUrls: ["./product-detail.component.scss"],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly productService = inject(ProductService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly messageService = inject(MessageService);
  private readonly destroyRef = inject(DestroyRef);
  readonly listQuery = inject(ProductListStateService).lastQuery;

  product = signal<Product | null>(null);
  loading = signal(true);
  actionLoading = signal(false);

  /** Ảnh đang xem trong gallery; null = dùng ảnh đầu tiên. */
  currentImageId = signal<number | null>(null);
  private readonly brokenImageIds = signal<ReadonlySet<number>>(new Set());
  private readonly legacyImageBroken = signal(false);

  images = computed<ProductImage[]>(() => this.product()?.images ?? []);
  currentImage = computed<ProductImage | null>(() => {
    const list = this.images();
    if (list.length === 0) {
      return null;
    }
    const selectedId = this.currentImageId();
    return list.find((image) => image.id === selectedId) ?? list[0];
  });
  /** Ảnh lớn: ưu tiên ảnh chọn trong gallery, fallback `imageUrl` cũ của sản phẩm legacy. */
  heroSrc = computed<string | null>(() => this.currentImage()?.url ?? this.product()?.imageUrl ?? null);
  heroBroken = computed<boolean>(() => {
    const image = this.currentImage();
    return image ? this.brokenImageIds().has(image.id) : this.legacyImageBroken();
  });

  productStatusLabelVi = productStatusLabelVi;
  platformLabelVi = productPlatformLabelVi;
  formatQuantity = formatQuantity;
  readonly lowStockThreshold = LOW_STOCK_THRESHOLD;

  constructor() {
    const id = Number(this.route.snapshot.paramMap.get("id"));
    if (!id) {
      this.loading.set(false);
      this.router.navigate(["/products"]);
      return;
    }
    this.load(id);
  }

  getSeverity(status: string): TagSeverity {
    return (PRODUCT_STATUS_SEVERITY[status as keyof typeof PRODUCT_STATUS_SEVERITY] as TagSeverity) ?? "secondary";
  }

  isArchived(): boolean {
    return this.product()?.status === "ARCHIVED";
  }

  isActive(): boolean {
    return this.product()?.status === "ACTIVE";
  }

  isInactive(): boolean {
    return this.product()?.status === "INACTIVE";
  }

  selectImage(imageId: number): void {
    this.currentImageId.set(imageId);
  }

  imageBroken(imageId: number): boolean {
    return this.brokenImageIds().has(imageId);
  }

  onHeroError(): void {
    const image = this.currentImage();
    if (!image) {
      this.legacyImageBroken.set(true);
      return;
    }
    this.markImageBroken(image.id);
  }

  onThumbError(imageId: number): void {
    this.markImageBroken(imageId);
  }

  private markImageBroken(imageId: number): void {
    this.brokenImageIds.update((current) => {
      const next = new Set(current);
      next.add(imageId);
      return next;
    });
  }

  edit(): void {
    const p = this.product();
    if (!p) return;
    this.router.navigate(["/products", p.id, "edit"]);
  }

  confirmArchive(): void {
    const p = this.product();
    if (!p) return;
    this.confirmationService.confirm({
      message: `Xoá sản phẩm "${p.name}"? Bạn có thể khôi phục lại sau.`,
      header: "Xác nhận xoá",
      icon: "pi pi-exclamation-triangle",
      acceptLabel: "Xoá",
      rejectLabel: "Huỷ",
      acceptButtonStyleClass: "p-button-danger",
      accept: () => this.archive(),
    });
  }

  archive(): void {
    const p = this.product();
    if (!p) return;
    this.actionLoading.set(true);
    this.productService.archiveProduct(p.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.messageService.add({ severity: "success", summary: "Thành công", detail: "Đã xoá sản phẩm." });
        this.load(p.id);
      },
      error: (err) => {
        this.actionLoading.set(false);
        const detail = err?.error?.message ?? "Không thể xoá sản phẩm.";
        this.messageService.add({ severity: "error", summary: "Lỗi", detail });
      },
    });
  }

  confirmRestore(): void {
    const p = this.product();
    if (!p) return;
    this.confirmationService.confirm({
      message: `Khôi phục sản phẩm "${p.name}" về trạng thái Đang bán?`,
      header: "Xác nhận khôi phục",
      icon: "pi pi-refresh",
      acceptLabel: "Khôi phục",
      rejectLabel: "Huỷ",
      acceptButtonStyleClass: "p-button-success",
      accept: () => this.restore(),
    });
  }

  restore(): void {
    const p = this.product();
    if (!p) return;
    this.actionLoading.set(true);
    this.productService.restoreProduct(p.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        this.actionLoading.set(false);
        this.product.set(updated);
        this.messageService.add({ severity: "success", summary: "Thành công", detail: "Đã khôi phục sản phẩm." });
      },
      error: (err) => {
        this.actionLoading.set(false);
        const detail = err?.error?.message ?? "Không thể khôi phục sản phẩm.";
        this.messageService.add({ severity: "error", summary: "Lỗi", detail });
      },
    });
  }

  confirmToggleInactive(): void {
    const p = this.product();
    if (!p) return;
    if (p.status === "ACTIVE") {
      this.confirmationService.confirm({
        message: `Chuyển sản phẩm "${p.name}" sang trạng thái Ngừng bán?`,
        header: "Xác nhận",
        icon: "pi pi-exclamation-triangle",
        acceptLabel: "Ngừng bán",
        rejectLabel: "Huỷ",
        acceptButtonStyleClass: "p-button-warning",
        accept: () => this.updateStatus("INACTIVE"),
      });
    } else if (p.status === "INACTIVE") {
      this.confirmationService.confirm({
        message: `Chuyển sản phẩm "${p.name}" sang trạng thái Đang bán?`,
        header: "Xác nhận",
        icon: "pi pi-check",
        acceptLabel: "Đang bán",
        rejectLabel: "Huỷ",
        acceptButtonStyleClass: "p-button-success",
        accept: () => this.updateStatus("ACTIVE"),
      });
    }
  }

  updateStatus(status: "ACTIVE" | "INACTIVE"): void {
    const p = this.product();
    if (!p) return;
    this.actionLoading.set(true);
    this.productService.updateStatus(p.id, status).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        this.actionLoading.set(false);
        this.product.set(updated);
        this.messageService.add({
          severity: "success",
          summary: "Thành công",
          detail: status === "ACTIVE" ? "Đã chuyển sang Đang bán." : "Đã chuyển sang Ngừng bán.",
        });
      },
      error: (err) => {
        this.actionLoading.set(false);
        const detail = err?.error?.message ?? "Không thể cập nhật trạng thái.";
        this.messageService.add({ severity: "error", summary: "Lỗi", detail });
      },
    });
  }

  priceRange(): string {
    const p = this.product();
    return p ? formatPriceRange(p.minPrice, p.maxPrice) : "—";
  }

  totalStockValue(): number {
    const p = this.product();
    if (!p) return 0;
    return p.skus.reduce((sum, s) => sum + s.price * s.stock, 0);
  }

  formatCurrency(v: number): string {
    return formatVnd(v);
  }

  formatDate(v: string | null | undefined): string {
    if (!v) return "—";
    try {
      const d = new Date(v);
      const day = String(d.getDate()).padStart(2, "0");
      const month = String(d.getMonth() + 1).padStart(2, "0");
      const year = d.getFullYear();
      const hours = String(d.getHours()).padStart(2, "0");
      const mins = String(d.getMinutes()).padStart(2, "0");
      return `${day}/${month}/${year} ${hours}:${mins}`;
    } catch {
      return v;
    }
  }

  private load(id: number): void {
    this.loading.set(true);
    this.productService.getProduct(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (product) => {
        this.product.set(product);
        this.applyCurrentImage(product);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        const detail = err?.error?.message ?? "Không tải được thông tin sản phẩm.";
        this.messageService.add({ severity: "error", summary: "Lỗi", detail });
        this.router.navigate(["/products"], { queryParams: this.listQuery() });
      },
    });
  }

  private applyCurrentImage(product: Product): void {
    const images = product.images ?? [];
    const primary = images.find((image) => image.primary);
    this.currentImageId.set(primary ? primary.id : (images[0]?.id ?? null));
    this.brokenImageIds.set(new Set<number>());
    this.legacyImageBroken.set(false);
  }
}

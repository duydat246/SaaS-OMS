import { Component, ChangeDetectionStrategy, inject, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { ActivatedRoute, Router, RouterLink } from "@angular/router";
import { MessageService, ConfirmationService } from "primeng/api";
import { CardModule } from "primeng/card";
import { ButtonModule } from "primeng/button";
import { TagModule } from "primeng/tag";
import { TableModule } from "primeng/table";
import { DialogModule } from "primeng/dialog";
import { ConfirmDialogModule } from "primeng/confirmdialog";
import { ToastModule } from "primeng/toast";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { Product } from "@features/products/models/product.model";
import { productStatusLabelVi } from "@features/products/models/product-status";
import { PRODUCT_STATUS_SEVERITY } from "@features/products/constants/product.constants";
import { ProductService } from "@features/products/services/product.service";

type TagSeverity =
  | "success"
  | "info"
  | "secondary"
  | "contrast"
  | "warning"
  | "danger";

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
    ToastModule,
  ],
  providers: [ConfirmationService, MessageService],
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

  product = signal<Product | null>(null);
  loading = signal(true);
  actionLoading = signal(false);

  productStatusLabelVi = productStatusLabelVi;

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

  edit(): void {
    const p = this.product();
    if (!p) return;
    this.router.navigate(["/products", p.id, "edit"]);
  }

  confirmArchive(): void {
    const p = this.product();
    if (!p) return;
    this.confirmationService.confirm({
      message: `Bạn có chắc chắn ngừng bán/xoá sản phẩm "${p.name}"?`,
      header: "Xác nhận",
      icon: "pi pi-exclamation-triangle",
      acceptLabel: "Xoá",
      rejectLabel: "Hủy",
      acceptButtonStyleClass: "p-button-danger",
      accept: () => this.archive(),
    });
  }

  archive(): void {
    const p = this.product();
    if (!p) return;
    this.actionLoading.set(true);
    this.productService.archiveProduct(p.id).pipe(takeUntilDestroyed()).subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.messageService.add({ severity: "success", summary: "Thành công", detail: "Đã ngừng bán sản phẩm." });
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
      rejectLabel: "Hủy",
      acceptButtonStyleClass: "p-button-success",
      accept: () => this.restore(),
    });
  }

  restore(): void {
    const p = this.product();
    if (!p) return;
    this.actionLoading.set(true);
    this.productService.restoreProduct(p.id).pipe(takeUntilDestroyed()).subscribe({
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
        rejectLabel: "Hủy",
        acceptButtonStyleClass: "p-button-warning",
        accept: () => this.updateStatus("INACTIVE"),
      });
    } else if (p.status === "INACTIVE") {
      this.confirmationService.confirm({
        message: `Chuyển sản phẩm "${p.name}" sang trạng thái Đang bán?`,
        header: "Xác nhận",
        icon: "pi pi-check",
        acceptLabel: "Đang bán",
        rejectLabel: "Hủy",
        acceptButtonStyleClass: "p-button-success",
        accept: () => this.updateStatus("ACTIVE"),
      });
    }
  }

  updateStatus(status: "ACTIVE" | "INACTIVE"): void {
    const p = this.product();
    if (!p) return;
    this.actionLoading.set(true);
    this.productService.updateStatus(p.id, status).pipe(takeUntilDestroyed()).subscribe({
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
    if (!p || p.skus.length === 0) return "—";
    if (p.minPrice != null && p.maxPrice != null) {
      if (p.minPrice === p.maxPrice) {
        return new Intl.NumberFormat("vi-VN").format(p.minPrice) + "₫";
      }
      return (
        new Intl.NumberFormat("vi-VN").format(p.minPrice) +
        "₫ - " +
        new Intl.NumberFormat("vi-VN").format(p.maxPrice) +
        "₫"
      );
    }
    const prices = p.skus.map((s) => s.price);
    const min = Math.min(...prices);
    const max = Math.max(...prices);
    if (min === max) return new Intl.NumberFormat("vi-VN").format(min) + "₫";
    return new Intl.NumberFormat("vi-VN").format(min) + "₫ - " + new Intl.NumberFormat("vi-VN").format(max) + "₫";
  }

  totalStockValue(): number {
    const p = this.product();
    if (!p) return 0;
    return p.skus.reduce((sum, s) => sum + s.price * s.stock, 0);
  }

  formatCurrency(v: number): string {
    return new Intl.NumberFormat("vi-VN").format(v) + "₫";
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
    this.productService.getProduct(id).pipe(takeUntilDestroyed()).subscribe({
      next: (product) => {
        this.product.set(product);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.router.navigate(["/products"]);
      },
    });
  }
}

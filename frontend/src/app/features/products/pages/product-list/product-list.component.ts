import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { Router, RouterLink } from "@angular/router";
import { TableModule, TablePageEvent } from "primeng/table";
import { ButtonModule } from "primeng/button";
import { InputTextModule } from "primeng/inputtext";
import { TagModule } from "primeng/tag";
import { DropdownModule } from "primeng/dropdown";
import { CardModule } from "primeng/card";
import { ImageModule } from "primeng/image";
import { Product, ProductFilters, ProductStatus } from "@features/products/models/product.model";
import { productStatusLabelVi } from "@features/products/models/product-status";
import { ProductService } from "@features/products/services/product.service";
import {
  PRODUCT_ROWS_PER_PAGE,
  PRODUCT_STATUS_SEVERITY,
} from "@features/products/constants/product.constants";
import { PageMeta } from "@core/models/api.model";
import { ExcelExportService } from "@shared/services/excel-export.service";
import { platformSourceLabelVi } from "@features/orders/models/platform-source";

type TagSeverity =
  | "success"
  | "info"
  | "secondary"
  | "contrast"
  | "warning"
  | "danger";

interface FilterOption {
  label: string;
  value: string | null;
}

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
    InputTextModule,
    TagModule,
    DropdownModule,
    CardModule,
    ImageModule,
  ],
  templateUrl: "./product-list.component.html",
  styleUrls: ["./product-list.component.scss"],
})
export class ProductListComponent implements OnInit {
  private readonly productService = inject(ProductService);
  private readonly excelExportService = inject(ExcelExportService);
  private readonly router = inject(Router);

  products = signal<Product[]>([]);
  meta = signal<PageMeta>({
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });
  loading = signal(false);

  searchText = "";
  selectedCategory: string | null = null;
  selectedStatus: string | null = null;
  selectedPlatform: string | null = null;

  categoryOptions: FilterOption[] = [{ label: "Tất cả danh mục", value: null }];
  statusOptions: FilterOption[] = [
    { label: "Tất cả", value: null },
    { label: "Đang bán", value: "ACTIVE" },
    { label: "Ngừng bán", value: "INACTIVE" },
    { label: "Đã xoá", value: "ARCHIVED" },
  ];
  platformOptions: FilterOption[] = [
    { label: "Tất cả nền tảng", value: null },
    { label: "Nhập tay", value: "MANUAL" },
    { label: "TikTok Shop", value: "TIKTOK" },
    { label: "Shopee", value: "SHOPEE" },
  ];
  rowsPerPageOptions = PRODUCT_ROWS_PER_PAGE;

  productStatusLabelVi = productStatusLabelVi;
  platformSourceLabelVi = platformSourceLabelVi;
  PRODUCT_STATUS_SEVERITY = PRODUCT_STATUS_SEVERITY;

  getStatusSeverity(status: ProductStatus): "success" | "secondary" | "info" | "warning" | "danger" | "contrast" {
    return (PRODUCT_STATUS_SEVERITY as any)[status];
  }

  ngOnInit(): void {
    this.loadCategories();
    this.load();
  }

  loadCategories(): void {
    this.productService.getCategories().subscribe({
      next: (categories) => {
        const options: FilterOption[] = [{ label: "Tất cả danh mục", value: null }];
        for (const category of categories ?? []) {
          if (category && category.trim()) {
            options.push({ label: category, value: category });
          }
        }
        this.categoryOptions = options;
      },
      error: () => {
        this.categoryOptions = [{ label: "Tất cả danh mục", value: null }];
      },
    });
  }

  load(page = 0): void {
    this.loading.set(true);
    const filters: ProductFilters = {
      page,
      size: this.meta().size,
      search: this.searchText?.trim() || undefined,
      category: this.selectedCategory ?? undefined,
      status: (this.selectedStatus as any) ?? undefined,
      platformSource: this.selectedPlatform ?? undefined,
    };

    this.productService.getProducts(filters).subscribe({
      next: (res) => {
        this.products.set(res.content ?? []);
        this.meta.set(res.meta);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  applyFilters(): void {
    this.load(0);
  }

  clearFilters(): void {
    this.searchText = "";
    this.selectedCategory = null;
    this.selectedStatus = null;
    this.selectedPlatform = null;
    this.load(0);
  }

  onPage(event: TablePageEvent): void {
    const nextSize = event.rows ?? this.meta().size;
    const nextPage = Math.floor((event.first ?? 0) / nextSize);

    if (nextSize !== this.meta().size) {
      this.meta.update((meta) => ({ ...meta, size: nextSize }));
    }

    this.load(nextPage);
  }

  goToDetail(id: number): void {
    this.router.navigate(["/products", id]);
  }

  goToEdit(id: number): void {
    this.router.navigate(["/products", id, "edit"]);
  }

  archiveProduct(product: Product): void {
    const confirmed = window.confirm(
      `Bạn có chắc chắn muốn lưu trữ sản phẩm "${product.name}"?`,
    );
    if (!confirmed) {
      return;
    }
    this.productService.archiveProduct(product.id).subscribe({
      next: () => {
        this.load(this.meta().page);
      },
    });
  }

  exportExcel(): void {
    this.excelExportService.exportProducts(this.products());
  }

  getStockSeverity(totalStock: number): TagSeverity {
    if (totalStock <= 0) {
      return "danger";
    }
    if (totalStock <= 5) {
      return "warning";
    }
    return "success";
  }

  getStockLabel(totalStock: number): string {
    if (totalStock <= 0) {
      return "Hết hàng";
    }
    if (totalStock <= 5) {
      return "Sắp hết";
    }
    return "Còn hàng";
  }

  getPriceLabel(product: Product): string {
    const minPrice = product.minPrice;
    const maxPrice = product.maxPrice;
    if (minPrice == null && maxPrice == null) {
      return "—";
    }
    if (minPrice != null && maxPrice != null && minPrice === maxPrice) {
      return this.formatCurrency(minPrice);
    }
    if (minPrice != null && maxPrice != null) {
      return `${this.formatCurrency(minPrice)} – ${this.formatCurrency(maxPrice)}`;
    }
    if (minPrice != null) {
      return this.formatCurrency(minPrice);
    }
    if (maxPrice != null) {
      return this.formatCurrency(maxPrice);
    }
    return "—";
  }

  private formatCurrency(value: number): string {
    return new Intl.NumberFormat("vi-VN", {
      style: "currency",
      currency: "VND",
      maximumFractionDigits: 0,
    }).format(value);
  }

  getImageSrc(product: Product): string {
    return product.imageUrl || "assets/images/placeholder.svg";
  }

  onImageError(event: Event): void {
    const target = event.target as HTMLImageElement;
    target.src = "assets/images/placeholder.svg";
  }
}

import { ProductStatus } from "@features/products/models/product.model";
import { formatVnd } from "@shared/utils/format-vnd";

export { formatVnd };

export type TagSeverity = "success" | "info" | "secondary" | "contrast" | "warning" | "danger";

export interface ProductOption<T = string> {
  label: string;
  value: T;
}

export const PRODUCT_STATUS_SEVERITY: Record<ProductStatus, TagSeverity> = {
  ACTIVE: "success",
  INACTIVE: "warning",
  ARCHIVED: "danger",
};

export const PRODUCT_STATUS_FILTER_OPTIONS: ProductOption<ProductStatus>[] = [
  { label: "Đang bán", value: "ACTIVE" },
  { label: "Ngừng bán", value: "INACTIVE" },
  { label: "Đã xoá", value: "ARCHIVED" },
];

const PLATFORM_LABEL_VI: Record<string, string> = {
  MANUAL: "Nhập tay",
  TIKTOK: "TikTok Shop",
  SHOPEE: "Shopee",
};

export const PRODUCT_PLATFORM_OPTIONS: ProductOption[] = Object.entries(PLATFORM_LABEL_VI).map(
  ([value, label]) => ({ label, value }),
);

export function productPlatformLabelVi(platform: string | null | undefined): string {
  if (!platform) return "—";
  return PLATFORM_LABEL_VI[platform] ?? platform;
}

export const PRODUCT_ROWS_PER_PAGE = [10, 20, 50];
export const PRODUCT_DEFAULT_PAGE_SIZE = 20;

export const PRODUCT_DEFAULT_SORT = "updatedAt,desc";
export const PRODUCT_SORT_OPTIONS: ProductOption[] = [
  { label: "Cập nhật gần đây", value: PRODUCT_DEFAULT_SORT },
  { label: "Mới tạo trước", value: "createdAt,desc" },
  { label: "Tên A → Z", value: "name,asc" },
  { label: "Tên Z → A", value: "name,desc" },
];

/** Tồn kho từ 1 đến ngưỡng này được coi là "Sắp hết", dùng chung cho mọi trang. */
export const LOW_STOCK_THRESHOLD = 5;

export function stockLabelVi(stock: number): string {
  if (stock <= 0) return "Hết hàng";
  return stock <= LOW_STOCK_THRESHOLD ? "Sắp hết" : "Còn hàng";
}

export function stockSeverity(stock: number): TagSeverity {
  if (stock <= 0) return "danger";
  return stock <= LOW_STOCK_THRESHOLD ? "warning" : "success";
}

export function formatPriceRange(min: number | null, max: number | null): string {
  if (min == null || max == null) return "—";
  return min === max ? formatVnd(min) : `${formatVnd(min)} – ${formatVnd(max)}`;
}

export function formatQuantity(value: number): string {
  return new Intl.NumberFormat("vi-VN").format(value);
}

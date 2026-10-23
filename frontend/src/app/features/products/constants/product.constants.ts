import { ProductStatus } from "@features/products/models/product.model";
import { ProductStatusCode, PRODUCT_STATUS_LABEL_VI } from "@features/products/models/product-status";

type TagSeverity = "success" | "info" | "secondary" | "contrast" | "warning" | "danger";

export const PRODUCT_STATUS_SEVERITY: Record<ProductStatus, TagSeverity> = {
  ACTIVE: "success",
  INACTIVE: "warning",
  ARCHIVED: "danger",
};

export const PRODUCT_STATUS_OPTIONS = [
  { label: "Đang bán", value: "ACTIVE" },
  { label: "Ngừng bán", value: "INACTIVE" },
];

export const PRODUCT_ROWS_PER_PAGE = [10, 20, 50];

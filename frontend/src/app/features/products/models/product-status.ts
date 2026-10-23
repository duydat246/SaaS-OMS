import type { ProductStatus } from "./product.model";

export enum ProductStatusCode {
  ACTIVE = "ACTIVE",
  INACTIVE = "INACTIVE",
  ARCHIVED = "ARCHIVED",
}

export const PRODUCT_STATUS_LABEL_VI: Record<ProductStatus, string> = {
  ACTIVE: "Đang bán",
  INACTIVE: "Ngừng bán",
  ARCHIVED: "Đã xoá",
};

export function productStatusLabelVi(status: ProductStatus | string | null | undefined): string {
  if (status == null || status === "") return "—";
  return PRODUCT_STATUS_LABEL_VI[status as ProductStatus] ?? status;
}

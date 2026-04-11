import type { OrderStatus } from "./order.model";

/**
 * Giá trị trùng chuỗi enum backend (OrderStatus).
 */
export enum OrderStatusCode {
  PENDING = "PENDING",
  CONFIRMED = "CONFIRMED",
  SHIPPED = "SHIPPED",
  DELIVERED = "DELIVERED",
  CANCELLED = "CANCELLED",
}

/** Nhãn tiếng Việt hiển thị theo mã trạng thái backend */
export const ORDER_STATUS_LABEL_VI: Record<OrderStatus, string> = {
  PENDING: "Chờ xử lý",
  CONFIRMED: "Đã xác nhận",
  SHIPPED: "Đang giao hàng",
  DELIVERED: "Đã giao hàng",
  CANCELLED: "Đã hủy",
};

/** Lấy nhãn tiếng Việt; nếu lạ (API mới) thì trả về mã gốc */
export function orderStatusLabelVi(status: string | null | undefined): string {
  if (status == null || status === "") return "—";
  return ORDER_STATUS_LABEL_VI[status as OrderStatus] ?? status;
}

import { OrderStatus } from "@features/orders/models/order.model";
import {
  OrderStatusCode,
  ORDER_STATUS_LABEL_VI,
} from "@features/orders/models/order-status";

type TagSeverity =
  | "success"
  | "info"
  | "secondary"
  | "contrast"
  | "warning"
  | "danger";

export const ORDER_STATUS_SEVERITY: Record<OrderStatus, TagSeverity> = {
  PENDING: "warning",
  CONFIRMED: "info",
  SHIPPED: "info",
  DELIVERED: "success",
  CANCELLED: "danger",
};

export const ORDER_STATUS_TRANSITIONS: Record<OrderStatus, OrderStatus[]> = {
  PENDING: ["CONFIRMED", "CANCELLED"],
  CONFIRMED: ["SHIPPED", "CANCELLED"],
  SHIPPED: ["DELIVERED", "CANCELLED"],
  DELIVERED: [],
  CANCELLED: [],
};

export const ORDER_STATUS_OPTIONS = [
  OrderStatusCode.PENDING,
  OrderStatusCode.CONFIRMED,
  OrderStatusCode.SHIPPED,
  OrderStatusCode.DELIVERED,
  OrderStatusCode.CANCELLED,
].map((value) => ({ label: ORDER_STATUS_LABEL_VI[value], value }));

export const ORDER_LIST_ROWS_PER_PAGE_OPTIONS = [10, 20, 50];

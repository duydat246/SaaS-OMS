export type OrderStatus =
  | "PENDING"
  | "CONFIRMED"
  | "SHIPPED"
  | "DELIVERED"
  | "CANCELLED";
export type PlatformSource = "TIKTOK" | "SHOPEE" | "MANUAL";

export interface Order {
  id: number;
  platformSource: PlatformSource;
  platformOrderId: string | null;
  status: OrderStatus;
  subtotal: number;
  shippingFee: number;
  discount: number;
  totalAmount: number;
  shippingAddress: string | null;
  notes: string | null;
  orderedAt: string | null;
  createdAt: string;
  updatedAt: string;
  customer: OrderCustomer | null;
  items: OrderItem[];
}

export interface OrderCustomer {
  id: number;
  fullName: string;
  phone: string | null;
  email: string | null;
  address: string | null;
}

export interface OrderItem {
  id: number;
  skuCode: string | null;
  productName: string;
  variant: string | null;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
}

export interface CreateOrderRequest {
  customerName: string;
  customerPhone: string;
  customerAddress: string;
  shippingFee: number;
  discount: number;
  notes: string;
  items: CreateOrderItemRequest[];
}

export interface CreateOrderItemRequest {
  productName: string;
  variant: string;
  quantity: number;
  unitPrice: number;
}

export interface OrderFilters {
  status?: OrderStatus;
  search?: string;
  page?: number;
  size?: number;
}

/** GET /api/orders/stats */
export interface OrderStats {
  totalOrders: number;
  pendingOrders: number;
  deliveredOrders: number;
  /** Backend sends JSON number; keep flexible for decimal strings */
  deliveredRevenue: number | string;
}

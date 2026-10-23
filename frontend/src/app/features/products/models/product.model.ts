import { PlatformSource } from "@features/orders/models/order.model";

export type ProductStatus = "ACTIVE" | "INACTIVE" | "ARCHIVED";

export interface Sku {
  id: number;
  skuCode: string;
  variant: string | null;
  price: number;
  stock: number;
  externalId: string | null;
}

export interface Product {
  id: number;
  name: string;
  description: string | null;
  category: string | null;
  platformSource: string;
  externalId: string | null;
  imageUrl: string | null;
  status: ProductStatus;
  createdAt: string;
  updatedAt: string;
  skuCount: number;
  minPrice: number | null;
  maxPrice: number | null;
  totalStock: number;
  skus: Sku[];
}

export interface ProductStats {
  totalProducts: number;
  activeProducts: number;
  inactiveProducts: number;
  outOfStockCount: number;
  totalStock: number;
}

export interface ProductFilters {
  page?: number;
  size?: number;
  status?: ProductStatus | "";
  category?: string;
  platformSource?: string;
  search?: string;
}

export interface SkuRequest {
  id?: number;
  skuCode: string;
  variant: string | null;
  price: number;
  stock: number;
}

export interface ProductRequest {
  name: string;
  description: string | null;
  category: string | null;
  platformSource: string;
  imageUrl: string | null;
  skus: SkuRequest[];
}

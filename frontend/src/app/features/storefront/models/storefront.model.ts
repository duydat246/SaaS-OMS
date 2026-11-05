/** Khớp `StorefrontShopResponse`. `contactPhone` luôn có field, `null` khi chủ shop chưa nhập. */
export interface StorefrontShop {
  slug: string;
  name: string;
  contactPhone: string | null;
}

/** Khớp `StorefrontProductSummaryResponse`. Sản phẩm không có SKU có giá `null` và `inStock = false`. */
export interface StorefrontProductSummary {
  id: number;
  name: string;
  category: string | null;
  imageUrl: string | null;
  minPrice: number | null;
  maxPrice: number | null;
  inStock: boolean;
}

export interface StorefrontProductImage {
  id: number;
  url: string;
  primary: boolean;
}

export interface StorefrontSku {
  id: number;
  variant: string | null;
  price: number | null;
  inStock: boolean;
}

/** Khớp `StorefrontProductDetailResponse`. */
export interface StorefrontProductDetail extends StorefrontProductSummary {
  description: string | null;
  images: StorefrontProductImage[];
  skus: StorefrontSku[];
}

export interface StorefrontProductFilters {
  search?: string;
  category?: string;
  sort?: string;
  page?: number;
  size?: number;
}

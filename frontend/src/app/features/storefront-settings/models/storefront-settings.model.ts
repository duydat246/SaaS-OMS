/** Khớp `ShopResponse` của `GET /api/shop` và `PUT /api/shop/storefront`. */
export interface StorefrontSettings {
  name: string;
  slug: string;
  storefrontEnabled: boolean;
  contactPhone: string | null;
}

/** Khớp `UpdateStorefrontRequest`. `contactPhone` là `null` thì xoá số đang lưu. */
export interface UpdateStorefrontRequest {
  storefrontEnabled: boolean;
  slug: string;
  contactPhone: string | null;
}

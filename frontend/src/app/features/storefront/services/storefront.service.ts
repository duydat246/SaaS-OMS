import { HttpClient, HttpParams } from "@angular/common/http";
import { inject, Injectable, signal } from "@angular/core";
import { map, Observable } from "rxjs";
import { ApiResponse, PageResponse } from "@core/models/api.model";
import {
  StorefrontProductDetail,
  StorefrontProductFilters,
  StorefrontProductSummary,
  StorefrontShop,
} from "@features/storefront/models/storefront.model";
import { STOREFRONT_PAGE_SIZE } from "@features/storefront/constants/storefront.constants";

@Injectable({ providedIn: "root" })
export class StorefrontService {
  private readonly http = inject(HttpClient);
  private readonly base = "/api/public/shops";

  /** Shop đang mở, do layout khách đặt. Trang con dùng để đặt tiêu đề tab. */
  readonly currentShop = signal<StorefrontShop | null>(null);

  getShop(slug: string): Observable<StorefrontShop> {
    return this.http
      .get<ApiResponse<StorefrontShop>>(this.shopUrl(slug))
      .pipe(map((r) => r.data));
  }

  getCategories(slug: string): Observable<string[]> {
    return this.http
      .get<ApiResponse<string[]>>(`${this.shopUrl(slug)}/categories`)
      .pipe(map((r) => r.data));
  }

  getProducts(
    slug: string,
    filters: StorefrontProductFilters = {},
  ): Observable<PageResponse<StorefrontProductSummary>> {
    let params = new HttpParams()
      .set("page", String(filters.page ?? 0))
      .set("size", String(filters.size ?? STOREFRONT_PAGE_SIZE));

    if (filters.search) {
      params = params.set("search", filters.search);
    }
    if (filters.category) {
      params = params.set("category", filters.category);
    }
    if (filters.sort) {
      params = params.set("sort", filters.sort);
    }

    return this.http
      .get<ApiResponse<PageResponse<StorefrontProductSummary>>>(`${this.shopUrl(slug)}/products`, { params })
      .pipe(map((r) => r.data));
  }

  getProduct(slug: string, id: number): Observable<StorefrontProductDetail> {
    return this.http
      .get<ApiResponse<StorefrontProductDetail>>(`${this.shopUrl(slug)}/products/${id}`)
      .pipe(map((r) => r.data));
  }

  private shopUrl(slug: string): string {
    return `${this.base}/${encodeURIComponent(slug)}`;
  }
}

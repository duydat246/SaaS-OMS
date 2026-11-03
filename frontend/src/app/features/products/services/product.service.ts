import { Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { EMPTY, Observable } from "rxjs";
import { expand, map, reduce } from "rxjs/operators";
import { ApiResponse, PageResponse } from "@core/models/api.model";
import { Product, ProductFilters, ProductRequest, ProductStats, ProductStatus } from "@features/products/models/product.model";

/** Backend kẹp size tối đa 100, nên xuất toàn bộ phải đi theo từng trang. */
const EXPORT_PAGE_SIZE = 100;

@Injectable({ providedIn: "root" })
export class ProductService {
  private readonly base = "/api/products";

  constructor(private http: HttpClient) {}

  getStats(): Observable<ProductStats> {
    return this.http
      .get<ApiResponse<ProductStats>>(`${this.base}/stats`)
      .pipe(map((r) => r.data));
  }

  getCategories(): Observable<string[]> {
    return this.http
      .get<ApiResponse<string[]>>(`${this.base}/categories`)
      .pipe(map((r) => r.data));
  }

  getProducts(filters: ProductFilters = {}): Observable<PageResponse<Product>> {
    let params = new HttpParams()
      .set("page", String(filters.page ?? 0))
      .set("size", String(filters.size ?? 20));

    if (filters.status) {
      params = params.set("status", filters.status);
    }
    if (filters.category) {
      params = params.set("category", filters.category);
    }
    if (filters.platformSource) {
      params = params.set("platformSource", filters.platformSource);
    }
    if (filters.search) {
      params = params.set("search", filters.search);
    }
    if (filters.sort) {
      params = params.set("sort", filters.sort);
    }

    return this.http
      .get<ApiResponse<PageResponse<Product>>>(this.base, { params })
      .pipe(map((r) => r.data));
  }

  /** Lấy mọi sản phẩm khớp bộ lọc, gọi tuần tự từng trang. */
  getAllProducts(filters: ProductFilters = {}): Observable<Product[]> {
    const fetchPage = (page: number) =>
      this.getProducts({ ...filters, page, size: EXPORT_PAGE_SIZE });

    return fetchPage(0).pipe(
      expand((res) =>
        res.meta.page + 1 < res.meta.totalPages ? fetchPage(res.meta.page + 1) : EMPTY,
      ),
      reduce((all, res) => all.concat(res.content ?? []), [] as Product[]),
    );
  }

  getProduct(id: number): Observable<Product> {
    return this.http
      .get<ApiResponse<Product>>(`${this.base}/${id}`)
      .pipe(map((r) => r.data));
  }

  createProduct(req: ProductRequest): Observable<Product> {
    return this.http
      .post<ApiResponse<Product>>(this.base, req)
      .pipe(map((r) => r.data));
  }

  updateProduct(id: number, req: ProductRequest): Observable<Product> {
    return this.http
      .put<ApiResponse<Product>>(`${this.base}/${id}`, req)
      .pipe(map((r) => r.data));
  }

  updateStatus(id: number, status: ProductStatus): Observable<Product> {
    return this.http
      .patch<ApiResponse<Product>>(`${this.base}/${id}/status`, { status })
      .pipe(map((r) => r.data));
  }

  archiveProduct(id: number): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.base}/${id}`)
      .pipe(map(() => undefined));
  }

  restoreProduct(id: number): Observable<Product> {
    return this.http
      .post<ApiResponse<Product>>(`${this.base}/${id}/restore`, {})
      .pipe(map((r) => r.data));
  }
}

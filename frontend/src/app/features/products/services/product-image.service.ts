import { inject, Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { ApiResponse } from "@core/models/api.model";
import { ProductImage } from "@features/products/models/product.model";

@Injectable({ providedIn: "root" })
export class ProductImageService {
  private readonly http = inject(HttpClient);
  private readonly base = "/api/products/images";

  /** Upload ảnh nháp (chưa gắn sản phẩm). */
  upload(file: File): Observable<ProductImage> {
    const formData = new FormData();
    formData.append("file", file, file.name);
    return this.http
      .post<ApiResponse<ProductImage>>(this.base, formData)
      .pipe(map((r) => r.data));
  }

  /** Xoá ảnh theo id; xử lý cả response bọc `ApiResponse` lẫn body rỗng. */
  delete(imageId: number): Observable<void> {
    return this.http
      .delete<ApiResponse<void | null> | null>(`${this.base}/${imageId}`)
      .pipe(
        map((response) => {
          if (response && typeof response === "object" && "data" in response) {
            return response.data ?? undefined;
          }
          return undefined;
        }),
      );
  }
}

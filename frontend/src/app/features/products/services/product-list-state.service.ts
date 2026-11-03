import { Injectable, signal } from "@angular/core";
import { Params } from "@angular/router";

/** Nhớ bộ lọc gần nhất của danh sách để nút "quay lại" ở trang chi tiết và form không làm mất bộ lọc. */
@Injectable({ providedIn: "root" })
export class ProductListStateService {
  readonly lastQuery = signal<Params>({});
}

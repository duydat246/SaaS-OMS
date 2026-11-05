import { HttpClient } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { map, Observable } from "rxjs";
import { ApiResponse } from "@core/models/api.model";
import {
  StorefrontSettings,
  UpdateStorefrontRequest,
} from "@features/storefront-settings/models/storefront-settings.model";

@Injectable({ providedIn: "root" })
export class StorefrontSettingsService {
  private readonly http = inject(HttpClient);
  private readonly base = "/api/shop";

  getSettings(): Observable<StorefrontSettings> {
    return this.http
      .get<ApiResponse<StorefrontSettings>>(this.base)
      .pipe(map((r) => r.data));
  }

  updateSettings(req: UpdateStorefrontRequest): Observable<StorefrontSettings> {
    return this.http
      .put<ApiResponse<StorefrontSettings>>(`${this.base}/storefront`, req)
      .pipe(map((r) => r.data));
  }
}

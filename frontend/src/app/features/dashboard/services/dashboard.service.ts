import { Injectable, inject } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { ApiResponse } from "@core/models/api.model";
import { DashboardOverview } from "@features/dashboard/models/dashboard.model";

@Injectable({ providedIn: "root" })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly base = "/api/dashboard";

  getOverview(days: number): Observable<DashboardOverview> {
    const params = new HttpParams().set("days", String(days));
    return this.http
      .get<ApiResponse<DashboardOverview>>(`${this.base}/overview`, { params })
      .pipe(map((r) => r.data));
  }
}

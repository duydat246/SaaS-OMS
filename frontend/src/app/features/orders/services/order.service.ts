import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { EMPTY, Observable } from 'rxjs';
import { expand, map, reduce } from 'rxjs/operators';
import { ApiResponse, PageResponse } from '@core/models/api.model';
import { CreateOrderRequest, Order, OrderFilters, OrderStats, OrderStatus } from '@features/orders/models/order.model';

/** Backend kẹp size tối đa 100, nên xuất toàn bộ phải đi theo từng trang. */
const EXPORT_PAGE_SIZE = 100;

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly base = '/api/orders';

  constructor(private http: HttpClient) {}

  getOrderStats(): Observable<OrderStats> {
    return this.http.get<ApiResponse<OrderStats>>(`${this.base}/stats`)
      .pipe(map(r => r.data));
  }

  getOrders(filters: OrderFilters = {}): Observable<PageResponse<Order>> {
    let params = new HttpParams()
      .set('page', filters.page ?? 0)
      .set('size', filters.size ?? 20)
      .set('sort', 'createdAt,desc');

    if (filters.status) params = params.set('status', filters.status);
    if (filters.search) params = params.set('search', filters.search);

    return this.http.get<ApiResponse<PageResponse<Order>>>(this.base, { params })
      .pipe(map(r => r.data));
  }

  /** Lấy mọi đơn khớp bộ lọc, gọi tuần tự từng trang; đơn mới đẩy dòng sang trang sau nên phải khử trùng theo id. */
  getAllOrders(filters: OrderFilters = {}): Observable<Order[]> {
    const fetchPage = (page: number) =>
      this.getOrders({ ...filters, page, size: EXPORT_PAGE_SIZE });

    return fetchPage(0).pipe(
      expand(res =>
        res.meta.page + 1 < res.meta.totalPages ? fetchPage(res.meta.page + 1) : EMPTY,
      ),
      reduce((byId, res) => {
        for (const order of res.content ?? []) byId.set(order.id, order);
        return byId;
      }, new Map<number, Order>()),
      map(byId => [...byId.values()]),
    );
  }

  getOrder(id: number): Observable<Order> {
    return this.http.get<ApiResponse<Order>>(`${this.base}/${id}`)
      .pipe(map(r => r.data));
  }

  createOrder(req: CreateOrderRequest): Observable<Order> {
    return this.http.post<ApiResponse<Order>>(this.base, req)
      .pipe(map(r => r.data));
  }

  updateStatus(id: number, status: OrderStatus): Observable<Order> {
    return this.http.patch<ApiResponse<Order>>(`${this.base}/${id}/status`, { status })
      .pipe(map(r => r.data));
  }

  deleteOrder(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}

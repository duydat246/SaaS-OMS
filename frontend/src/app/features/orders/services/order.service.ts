import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ApiResponse, PageResponse } from '@core/models/api.model';
import { CreateOrderRequest, Order, OrderFilters, OrderStats, OrderStatus } from '@features/orders/models/order.model';

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

import { Component, OnInit, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { Router, RouterLink } from "@angular/router";
import { FormsModule } from "@angular/forms";
import { TableModule, TablePageEvent } from "primeng/table";
import { ButtonModule } from "primeng/button";
import { InputTextModule } from "primeng/inputtext";
import { TagModule } from "primeng/tag";
import { DropdownModule } from "primeng/dropdown";
import {
  Order,
  OrderFilters,
  OrderStatus,
} from "@features/orders/models/order.model";
import { orderStatusLabelVi } from "@features/orders/models/order-status";
import { platformSourceLabelVi } from "@features/orders/models/platform-source";
import { OrderService } from "@features/orders/services/order.service";
import { PageMeta } from "@core/models/api.model";
import {
  ORDER_LIST_ROWS_PER_PAGE_OPTIONS,
  ORDER_STATUS_OPTIONS,
  ORDER_STATUS_SEVERITY,
} from "@features/orders/constants/order.constants";

type TagSeverity =
  | "success"
  | "info"
  | "secondary"
  | "contrast"
  | "warning"
  | "danger";

@Component({
  selector: "app-order-list",
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    TagModule,
    DropdownModule,
  ],
  templateUrl: "./order-list.component.html",
  styleUrls: ["./order-list.component.scss"],
})
export class OrderListComponent implements OnInit {
  orders = signal<Order[]>([]);
  meta = signal<PageMeta>({
    page: 0,
    size: 10,
    totalElements: 0,
    totalPages: 0,
  });
  loading = signal(false);

  searchText = "";
  selectedStatus: OrderStatus | null = null;

  statusOptions = ORDER_STATUS_OPTIONS;
  rowsPerPageOptions = ORDER_LIST_ROWS_PER_PAGE_OPTIONS;

  constructor(
    private orderService: OrderService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(page = 0): void {
    this.loading.set(true);
    const filters: OrderFilters = {
      page,
      size: this.meta().size,
      search: this.searchText || undefined,
      status: this.selectedStatus ?? undefined,
    };

    this.orderService.getOrders(filters).subscribe({
      next: (res) => {
        this.orders.set(res.content);
        this.meta.set(res.meta);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  applyFilters(): void {
    this.load(0);
  }

  clearSearch(): void {
    this.searchText = "";
    this.applyFilters();
  }

  onPage(event: TablePageEvent): void {
    const nextSize = event.rows ?? this.meta().size;
    const nextPage = Math.floor((event.first ?? 0) / nextSize);

    if (nextSize !== this.meta().size) {
      this.meta.update((meta) => ({ ...meta, size: nextSize }));
    }

    this.load(nextPage);
  }

  goToDetail(id: number): void {
    this.router.navigate(["/orders", id]);
  }

  getSeverity(status: OrderStatus): TagSeverity {
    return ORDER_STATUS_SEVERITY[status] ?? "secondary";
  }

  orderStatusLabelVi = orderStatusLabelVi;
  platformSourceLabelVi = platformSourceLabelVi;
}

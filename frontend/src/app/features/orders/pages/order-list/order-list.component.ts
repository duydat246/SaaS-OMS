import { Component, OnInit, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { Router, RouterLink } from "@angular/router";
import { FormsModule } from "@angular/forms";
import { TableModule, TablePageEvent } from "primeng/table";
import { ButtonModule } from "primeng/button";
import { InputTextModule } from "primeng/inputtext";
import { TagModule } from "primeng/tag";
import { DropdownModule } from "primeng/dropdown";
import { CardModule } from "primeng/card";
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
    CardModule,
  ],
  template: `
    <div class="flex flex-column gap-3">
      <!-- Header -->
      <div
        class="flex align-items-center justify-content-between flex-wrap gap-2"
      >
        <h2 class="m-0">Đơn hàng</h2>
        <a routerLink="/orders/new" class="w-full sm:w-auto">
          <p-button
            label="Tạo đơn hàng"
            icon="pi pi-plus"
            size="small"
            styleClass="w-full sm:w-auto"
          />
        </a>
      </div>

      <!-- Filters -->
      <p-card>
        <div class="order-list-filters">
          <div class="order-list-filters__inputs">
            <div class="order-search-input">
              <input
                pInputText
                [(ngModel)]="searchText"
                placeholder="Tìm khách hàng hoặc mã đơn..."
                class="w-full"
                style="padding-right:2.25rem"
                (keyup.enter)="applyFilters()"
              />
              @if (searchText) {
                <button
                  type="button"
                  class="order-search-input__clear"
                  (click)="clearSearch()"
                  aria-label="Xóa tìm kiếm"
                >
                  <i class="pi pi-times"></i>
                </button>
              }
            </div>
            <p-dropdown
              class="order-list-status-dd"
              [(ngModel)]="selectedStatus"
              [options]="statusOptions"
              optionLabel="label"
              optionValue="value"
              placeholder="Tất cả trạng thái"
              [showClear]="true"
              (onChange)="applyFilters()"
            />
          </div>
          <p-button
            label="Tìm kiếm"
            icon="pi pi-search"
            size="small"
            styleClass="order-list-search-btn"
            (click)="applyFilters()"
          />
        </div>
      </p-card>

      <!-- Table -->
      <p-table
        [value]="orders()"
        [loading]="loading()"
        [rows]="meta().size"
        [totalRecords]="meta().totalElements"
        [lazy]="true"
        (onPage)="onPage($event)"
        [paginator]="true"
        [rowsPerPageOptions]="rowsPerPageOptions"
        [showCurrentPageReport]="true"
        [scrollable]="true"
        scrollHeight="calc(100dvh - 14rem)"
        styleClass="p-datatable-sm apollo-order-table"
        [rowHover]="true"
      >
        <ng-template pTemplate="header">
          <tr>
            <th>Mã đơn</th>
            <th>Khách hàng</th>
            <th>Nền tảng</th>
            <th>Trạng thái</th>
            <th class="text-right">Tổng tiền</th>
            <th>Ngày tạo</th>
            <th></th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-order>
          <tr class="cursor-pointer" (click)="goToDetail(order.id)">
            <td>
              <code>#{{ order.id }}</code>
            </td>
            <td>{{ order.customer?.fullName ?? "—" }}</td>
            <td>
              <p-tag
                [value]="platformSourceLabelVi(order.platformSource)"
                severity="secondary"
              />
            </td>
            <td>
              <p-tag
                [value]="orderStatusLabelVi(order.status)"
                [severity]="getSeverity(order.status)"
              />
            </td>
            <td class="text-right font-medium">
              {{ order.totalAmount | number: "1.0-0" }}₫
            </td>
            <td>{{ order.createdAt | date: "dd/MM HH:mm" }}</td>
            <td (click)="$event.stopPropagation()">
              <p-button
                icon="pi pi-eye"
                [rounded]="true"
                [text]="true"
                size="small"
                (click)="goToDetail(order.id)"
              />
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr>
            <td colspan="7" class="text-center py-4 text-500">
              Không tìm thấy đơn hàng
            </td>
          </tr>
        </ng-template>
      </p-table>
    </div>
  `,
  styles: [
    `
      .order-list-filters {
        display: flex;
        flex-direction: column;
        gap: 0.65rem;
      }

      .order-list-filters__inputs {
        display: flex;
        flex-wrap: wrap;
        align-items: stretch;
        gap: 0.5rem;
        width: 100%;
      }

      .order-search-input {
        position: relative;
        min-width: 0;
      }

      .order-search-input__clear {
        position: absolute;
        top: 50%;
        right: 0.5rem;
        transform: translateY(-50%);
        border: 0;
        background: transparent;
        color: var(--text-color-secondary);
        width: 1.5rem;
        height: 1.5rem;
        border-radius: 999px;
        padding: 0;
        cursor: pointer;
        display: inline-flex;
        align-items: center;
        justify-content: center;
      }

      .order-search-input__clear:hover {
        background: transparent;
        color: var(--text-color);
      }

      :host ::ng-deep .order-list-status-dd .p-dropdown {
        width: 100%;
        min-width: 0;
      }

      :host ::ng-deep .order-list-search-btn {
        width: 100%;
      }

      @media (min-width: 768px) {
        .order-list-filters {
          flex-direction: row;
          flex-wrap: wrap;
          align-items: center;
          gap: 0.5rem;
        }

        .order-list-filters__inputs {
          flex: 1 1 auto;
          flex-wrap: nowrap;
          min-width: 0;
          width: auto;
        }

        .order-search-input {
          flex: 0 1 auto;
          max-width: 280px;
          width: min(100%, 280px);
        }

        :host ::ng-deep .order-list-status-dd {
          flex: 0 0 auto;
          width: min(100%, 240px);
        }

        :host ::ng-deep .order-list-status-dd .p-dropdown {
          width: 100% !important;
          min-width: 200px;
        }

        :host ::ng-deep .order-list-search-btn {
          width: auto;
          flex: 0 0 auto;
        }
      }

      @media (max-width: 767.98px) {
        .order-list-filters__inputs .order-search-input {
          flex: 1 1 calc(50% - 0.25rem);
          max-width: none;
          width: auto;
        }

        :host ::ng-deep .order-list-filters__inputs .order-list-status-dd {
          display: flex;
          flex: 1 1 calc(50% - 0.25rem);
          min-width: 0;
          width: auto;
          align-items: stretch;
        }

        :host ::ng-deep .order-list-filters__inputs .order-list-status-dd .p-dropdown {
          flex: 1 1 auto;
          width: 100% !important;
          min-width: 0 !important;
        }
      }
    `,
  ],
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

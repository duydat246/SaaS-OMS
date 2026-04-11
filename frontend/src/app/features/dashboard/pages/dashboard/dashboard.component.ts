import { Component, DestroyRef, OnInit, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { CommonModule } from "@angular/common";
import { RouterLink } from "@angular/router";
import { FormsModule } from "@angular/forms";
import { Subject, debounceTime, from, switchMap } from "rxjs";
import { TableLazyLoadEvent } from "primeng/table";
import { ChartModule } from "primeng/chart";
import { TableModule } from "primeng/table";
import { ButtonModule } from "primeng/button";
import { TagModule } from "primeng/tag";
import { DropdownModule } from "primeng/dropdown";
import { InputTextModule } from "primeng/inputtext";
import { IconFieldModule } from "primeng/iconfield";
import { InputIconModule } from "primeng/inputicon";
import { TooltipModule } from "primeng/tooltip";
import { AuthService } from "@core/auth/auth.service";
import { OrderService } from "@features/orders/services/order.service";
import { ExcelExportService } from "@shared/services/excel-export.service";
import {
  Order,
  OrderStats,
  OrderStatus,
} from "@features/orders/models/order.model";
import { orderStatusLabelVi } from "@features/orders/models/order-status";
import { platformSourceLabelVi } from "@features/orders/models/platform-source";
import {
  DASHBOARD_PERIOD_OPTIONS,
  DASHBOARD_TOP_PRODUCTS,
} from "@features/dashboard/constants/dashboard.constants";
import { ORDER_STATUS_SEVERITY } from "@features/orders/constants/order.constants";

export interface DashboardStatCard {
  label: string;
  value: string;
  /** When set, shows trend row with arrow styling */
  trend: string | null;
  up: boolean;
}

type StatusSeverity =
  | "success"
  | "info"
  | "secondary"
  | "contrast"
  | "warning"
  | "danger";

@Component({
  selector: "app-dashboard",
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    FormsModule,
    ChartModule,
    TableModule,
    ButtonModule,
    TagModule,
    DropdownModule,
    InputTextModule,
    IconFieldModule,
    InputIconModule,
    TooltipModule,
  ],
  templateUrl: "./dashboard.component.html",
  styleUrls: ["./dashboard.component.scss"],
})
export class DashboardComponent implements OnInit {
  recentOrders = signal<Order[]>([]);
  totalRecords = signal(0);
  tableFirst = signal(0);
  searchTerm = signal("");
  tableLoading = signal(true);
  statsLoading = signal(true);
  exportLoading = signal(false);
  stats = signal<DashboardStatCard[]>([
    { label: "Tổng đơn", value: "—", trend: null, up: true },
    { label: "Chờ xử lý", value: "—", trend: null, up: true },
    { label: "Đã giao", value: "—", trend: null, up: true },
    { label: "Doanh thu", value: "—", trend: null, up: true },
  ]);

  periodOptions = DASHBOARD_PERIOD_OPTIONS;
  selectedPeriod = "week";

  topProducts = DASHBOARD_TOP_PRODUCTS;

  barData = {
    labels: ["T2", "T3", "T4", "T5", "T6", "T7", "CN"],
    datasets: [
      {
        label: "Doanh thu",
        data: [42, 55, 48, 62, 58, 70, 52],
        backgroundColor: "#475569",
        borderRadius: 6,
        barPercentage: 0.65,
      },
      {
        label: "Lợi nhuận",
        data: [18, 24, 22, 28, 26, 32, 24],
        backgroundColor: "#e2e8f0",
        borderRadius: 6,
        barPercentage: 0.65,
      },
    ],
  };

  barOptions = {
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: "bottom" as const,
        labels: { color: "#94a3b8", padding: 16, usePointStyle: true },
      },
    },
    scales: {
      x: {
        ticks: { color: "#64748b" },
        grid: { color: "rgba(148, 163, 184, 0.08)" },
      },
      y: {
        beginAtZero: true,
        max: 90,
        ticks: { color: "#64748b" },
        grid: { color: "rgba(148, 163, 184, 0.1)" },
      },
    },
  };

  doughnutData = {
    labels: ["Điện tử", "Thời trang", "Gia dụng"],
    datasets: [
      {
        data: [45, 35, 20],
        backgroundColor: ["#1e3a5f", "#4f6a9e", "#e2e8f0"],
        borderWidth: 0,
      },
    ],
  };

  doughnutOptions = {
    maintainAspectRatio: false,
    cutout: "62%",
    plugins: {
      legend: {
        position: "bottom" as const,
        labels: { color: "#94a3b8", padding: 14, usePointStyle: true },
      },
    },
  };

  readonly PAGE_SIZE = 5;
  private readonly destroyRef = inject(DestroyRef);
  private readonly loadSubject = new Subject<{
    page: number;
    search: string;
  }>();

  private readonly excelExport = inject(ExcelExportService);

  constructor(
    public auth: AuthService,
    private orderService: OrderService,
  ) {}

  ngOnInit(): void {
    this.orderService.getOrderStats().subscribe({
      next: (orderStats) => {
        this.stats.set(this.buildStatCards(orderStats));
        this.statsLoading.set(false);
      },
      error: () => this.statsLoading.set(false),
    });

    this.loadSubject
      .pipe(
        debounceTime(200),
        switchMap(({ page, search }) => {
          this.tableLoading.set(true);
          return this.orderService.getOrders({
            page,
            size: this.PAGE_SIZE,
            search: search || undefined,
          });
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (res) => {
          this.recentOrders.set(res.content);
          this.totalRecords.set(res.meta.totalElements);
          this.tableLoading.set(false);
        },
        error: () => this.tableLoading.set(false),
      });
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    const first = event.first ?? 0;
    this.tableFirst.set(first);
    this.loadSubject.next({
      page: Math.floor(first / this.PAGE_SIZE),
      search: this.searchTerm(),
    });
  }

  onSearchChange(value: string): void {
    this.searchTerm.set(value);
    this.tableFirst.set(0);
    this.loadSubject.next({ page: 0, search: value });
  }

  exportOrders(): void {
    if (this.exportLoading()) return;
    this.exportLoading.set(true);
    this.orderService
      .getOrders({
        page: 0,
        size: 1000,
        search: this.searchTerm() || undefined,
      })
      .pipe(
        switchMap((res) =>
          from(
            this.excelExport.exportOrders(res.content, "danh-sach-don-hang"),
          ),
        ),
      )
      .subscribe({
        next: () => this.exportLoading.set(false),
        error: () => this.exportLoading.set(false),
      });
  }

  private buildStatCards(s: OrderStats): DashboardStatCard[] {
    const revenue = this.parseAmount(s.deliveredRevenue);
    return [
      {
        label: "Tổng đơn",
        value: this.formatCount(s.totalOrders),
        trend: null,
        up: true,
      },
      {
        label: "Chờ xử lý",
        value: this.formatCount(s.pendingOrders),
        trend: null,
        up: true,
      },
      {
        label: "Đã giao",
        value: this.formatCount(s.deliveredOrders),
        trend: null,
        up: true,
      },
      {
        label: "Doanh thu",
        value: this.formatCompactVnd(revenue),
        trend: null,
        up: true,
      },
    ];
  }

  private parseAmount(v: number | string): number {
    if (typeof v === "number") return v;
    const n = Number(v);
    return Number.isFinite(n) ? n : 0;
  }

  private formatCount(n: number): string {
    return Number.isFinite(n) ? String(Math.max(0, Math.trunc(n))) : "0";
  }

  /** Short VND for cards, e.g. ₫ 42M */
  private formatCompactVnd(amount: number): string {
    const v = Math.abs(amount);
    if (v >= 1_000_000_000) {
      const b = amount / 1_000_000_000;
      const t = b % 1 === 0 ? b.toFixed(0) : b.toFixed(1);
      return `₫ ${t}B`;
    }
    if (v >= 1_000_000) {
      return `₫ ${Math.round(amount / 1_000_000)}M`;
    }
    if (v >= 1_000) {
      return `₫ ${Math.round(amount / 1_000)}K`;
    }
    return `₫ ${Math.round(amount)}`;
  }

  getSeverity(status: OrderStatus): StatusSeverity {
    return ORDER_STATUS_SEVERITY[status];
  }

  statusLabel(status: OrderStatus): string {
    return orderStatusLabelVi(status);
  }

  platformLabel(platformSource: string | null | undefined): string {
    return platformSourceLabelVi(platformSource);
  }
}

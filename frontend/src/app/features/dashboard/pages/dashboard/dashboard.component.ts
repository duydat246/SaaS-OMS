import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { CommonModule } from "@angular/common";
import { RouterLink } from "@angular/router";
import { FormsModule } from "@angular/forms";
import {
  Subject,
  catchError,
  debounceTime,
  finalize,
  from,
  map,
  of,
  switchMap,
} from "rxjs";
import { MessageService } from "primeng/api";
import { TableLazyLoadEvent } from "primeng/table";
import { ChartModule } from "primeng/chart";
import { SelectButtonModule } from "primeng/selectbutton";
import { TableModule } from "primeng/table";
import { ButtonModule } from "primeng/button";
import { TagModule } from "primeng/tag";
import { InputTextModule } from "primeng/inputtext";
import { IconFieldModule } from "primeng/iconfield";
import { InputIconModule } from "primeng/inputicon";
import { TooltipModule } from "primeng/tooltip";
import { AuthService } from "@core/auth/auth.service";
import { OrderService } from "@features/orders/services/order.service";
import { ExcelExportService } from "@shared/services/excel-export.service";
import { formatVnd } from "@shared/utils/format-vnd";
import {
  Order,
  OrderStats,
  OrderStatus,
} from "@features/orders/models/order.model";
import { orderStatusLabelVi } from "@features/orders/models/order-status";
import { platformSourceLabelVi } from "@features/orders/models/platform-source";
import {
  DASHBOARD_CATEGORY_COLORS,
  DASHBOARD_CATEGORY_KIND_LABEL,
  DASHBOARD_DEFAULT_DAYS,
  DASHBOARD_DELIVERED_COLOR,
  DASHBOARD_NEUTRAL_COLORS,
  DASHBOARD_OPEN_COLOR,
  DASHBOARD_PERIOD_OPTIONS,
} from "@features/dashboard/constants/dashboard.constants";
import {
  DashboardCategoryKind,
  DashboardOverview,
  DashboardTopProduct,
} from "@features/dashboard/models/dashboard.model";
import { DashboardService } from "@features/dashboard/services/dashboard.service";
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

type UnnamedKind = Exclude<DashboardCategoryKind, "CATEGORY">;

interface DashboardCategoryRow {
  label: string;
  amount: number;
  quantity: number;
  color: string;
}

interface BarTooltipItem {
  dataset: { label?: string };
  parsed: { y: number };
}

interface DoughnutTooltipItem {
  parsed: number;
}

const REVENUE_CARD_LABEL = "Doanh thu đã giao (mọi thời gian)";

const CHART_TEXT_COLOR = "#5b6775";
const CHART_GRID_COLOR = "#dfe3e8";
const CHART_SURFACE_COLOR = "#ffffff";

const COMPACT_VND = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
  notation: "compact",
  maximumFractionDigits: 1,
});

/** `yyyy-MM-dd` là ngày Việt Nam: tách chuỗi, không qua `new Date` để khỏi lệch múi giờ. */
function formatShortDate(date: string): string {
  const [, month, day] = date.split("-");
  return `${day}/${month}`;
}

function formatFullDate(date: string): string {
  const [year, month, day] = date.split("-");
  return `${day}/${month}/${year}`;
}

/** Trang đánh số 1..total; delta = số trang lân cận quanh trang hiện tại. */
function buildPaginationItems(
  current: number,
  total: number,
  delta: number,
): Array<number | "ellipsis"> {
  if (total <= 1) return [];
  if (total <= delta * 2 + 3) {
    return Array.from({ length: total }, (_, i) => i + 1);
  }
  const pages: Array<number | "ellipsis"> = [];
  const left = Math.max(2, current - delta);
  const right = Math.min(total - 1, current + delta);

  pages.push(1);
  if (left > 2) pages.push("ellipsis");
  for (let i = left; i <= right; i++) pages.push(i);
  if (right < total - 1) pages.push("ellipsis");
  pages.push(total);
  return pages;
}

@Component({
  selector: "app-dashboard",
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    FormsModule,
    ChartModule,
    SelectButtonModule,
    TableModule,
    ButtonModule,
    TagModule,
    InputTextModule,
    IconFieldModule,
    InputIconModule,
    TooltipModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
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
  statsError = signal(false);
  tableError = signal<string | null>(null);
  exportLoading = signal(false);
  stats = signal<DashboardStatCard[]>([
    { label: "Tổng đơn", value: "—", trend: null, up: true },
    { label: "Chờ xử lý", value: "—", trend: null, up: true },
    { label: "Đã giao", value: "—", trend: null, up: true },
    { label: REVENUE_CARD_LABEL, value: "—", trend: null, up: true },
  ]);

  readonly periodOptions = DASHBOARD_PERIOD_OPTIONS;
  readonly selectedDays = signal(DASHBOARD_DEFAULT_DAYS);
  readonly overview = signal<DashboardOverview | null>(null);
  readonly overviewLoading = signal(true);
  readonly overviewError = signal<string | null>(null);

  readonly periodRange = computed(() => {
    const data = this.overview();
    return data
      ? `${formatFullDate(data.fromDate)} – ${formatFullDate(data.toDate)}`
      : "";
  });

  readonly hasRevenue = computed(() =>
    (this.overview()?.revenueByDay ?? []).some(
      (day) => day.deliveredRevenue !== 0 || day.openAmount !== 0,
    ),
  );

  readonly barData = computed(() => {
    const days = this.overview()?.revenueByDay ?? [];
    return {
      labels: days.map((day) => formatShortDate(day.date)),
      datasets: [
        {
          label: "Doanh thu",
          data: days.map((day) => day.deliveredRevenue),
          backgroundColor: DASHBOARD_DELIVERED_COLOR,
          borderRadius: 4,
          categoryPercentage: 0.8,
          barPercentage: 0.9,
          maxBarThickness: 24,
        },
        {
          label: "Đơn đang xử lý",
          data: days.map((day) => day.openAmount),
          backgroundColor: DASHBOARD_OPEN_COLOR,
          borderRadius: 4,
          categoryPercentage: 0.8,
          barPercentage: 0.9,
          maxBarThickness: 24,
        },
      ],
    };
  });

  readonly barSummary = computed(() => {
    const days = this.overview()?.revenueByDay ?? [];
    const delivered = days.reduce((sum, day) => sum + day.deliveredRevenue, 0);
    const open = days.reduce((sum, day) => sum + day.openAmount, 0);
    return `Doanh thu trong kỳ ${formatVnd(delivered)}, đơn đang xử lý ${formatVnd(open)}.`;
  });

  readonly barOptions = {
    maintainAspectRatio: false,
    interaction: { mode: "index" as const, intersect: false },
    plugins: {
      legend: {
        position: "bottom" as const,
        labels: { color: CHART_TEXT_COLOR, padding: 16, usePointStyle: true },
      },
      tooltip: {
        callbacks: {
          label: (item: BarTooltipItem) =>
            `${item.dataset.label}: ${formatVnd(item.parsed.y)}`,
        },
      },
    },
    scales: {
      x: {
        ticks: { color: CHART_TEXT_COLOR, maxRotation: 0, autoSkipPadding: 12 },
        grid: { display: false },
      },
      y: {
        beginAtZero: true,
        ticks: {
          color: CHART_TEXT_COLOR,
          callback: (value: number | string) =>
            COMPACT_VND.format(Number(value)),
        },
        grid: { color: CHART_GRID_COLOR },
      },
    },
  };

  readonly categoryRows = computed<DashboardCategoryRow[]>(() => {
    let namedIndex = 0;
    return (this.overview()?.categories ?? []).map((category) => {
      const named = category.kind === "CATEGORY";
      return {
        label: named
          ? (category.name ?? "—")
          : DASHBOARD_CATEGORY_KIND_LABEL[category.kind as UnnamedKind],
        amount: category.amount,
        quantity: category.quantity,
        color: named
          ? DASHBOARD_CATEGORY_COLORS[
              namedIndex++ % DASHBOARD_CATEGORY_COLORS.length
            ]
          : DASHBOARD_NEUTRAL_COLORS[category.kind as UnnamedKind],
      };
    });
  });

  readonly hasCategories = computed(() =>
    this.categoryRows().some((row) => row.amount > 0),
  );

  readonly doughnutData = computed(() => {
    const rows = this.categoryRows();
    return {
      labels: rows.map((row) => row.label),
      datasets: [
        {
          data: rows.map((row) => row.amount),
          backgroundColor: rows.map((row) => row.color),
          borderColor: CHART_SURFACE_COLOR,
          borderWidth: 2,
        },
      ],
    };
  });

  readonly categorySummary = computed(() =>
    this.categoryRows()
      .map((row) => `${row.label}: ${formatVnd(row.amount)}`)
      .join("; "),
  );

  readonly topProducts = computed(() => this.overview()?.topProducts ?? []);

  readonly doughnutOptions = {
    maintainAspectRatio: false,
    cutout: "62%",
    plugins: {
      // Chú giải là danh sách HTML bên dưới, có cả số tiền.
      legend: { display: false },
      tooltip: {
        callbacks: {
          label: (item: DoughnutTooltipItem) => formatVnd(item.parsed),
        },
      },
    },
  };

  readonly PAGE_SIZE = 5;
  /** Số trang số hiển thị quanh trang hiện tại (kiểu 1 … 2 3 4 … 10). */
  private readonly recentPageDelta = 2;

  readonly pageCount = computed(() =>
    Math.ceil(this.totalRecords() / this.PAGE_SIZE),
  );

  readonly showRecentPaginator = computed(() => this.pageCount() > 1);

  readonly currentPage1Based = computed(
    () => Math.floor(this.tableFirst() / this.PAGE_SIZE) + 1,
  );

  readonly paginationItems = computed(() =>
    buildPaginationItems(
      this.currentPage1Based(),
      this.pageCount(),
      this.recentPageDelta,
    ),
  );

  readonly recentPageReport = computed(() => {
    const total = this.totalRecords();
    if (total <= 0) return "";
    const first = this.tableFirst() + 1;
    const last = Math.min(this.tableFirst() + this.PAGE_SIZE, total);
    return `${first}–${last} / ${total}`;
  });

  private readonly destroyRef = inject(DestroyRef);
  private readonly loadSubject = new Subject<{
    page: number;
    search: string;
  }>();

  private readonly overviewRequests = new Subject<number>();

  private readonly excelExport = inject(ExcelExportService);
  private readonly messageService = inject(MessageService);
  private readonly orderService = inject(OrderService);
  private readonly dashboardService = inject(DashboardService);
  readonly auth = inject(AuthService);

  ngOnInit(): void {
    this.loadStats();

    // catchError nằm trong switchMap: một lần lỗi không làm chết luồng, kỳ cũ bị huỷ nên không đè kỳ mới.
    this.overviewRequests
      .pipe(
        switchMap((days) => {
          this.overviewLoading.set(true);
          this.overviewError.set(null);
          return this.dashboardService.getOverview(days).pipe(
            catchError((err) => {
              this.overviewError.set(
                err?.error?.message ?? "Không tải được số liệu tổng quan.",
              );
              return of(null);
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((overview) => {
        this.overview.set(overview);
        this.overviewLoading.set(false);
      });
    this.reloadOverview();

    this.loadSubject
      .pipe(
        debounceTime(200),
        switchMap(({ page, search }) => {
          this.tableLoading.set(true);
          this.tableError.set(null);
          return this.orderService
            .getOrders({
              page,
              size: this.PAGE_SIZE,
              search: search || undefined,
            })
            .pipe(
              catchError((err) => {
                this.tableError.set(
                  err?.error?.message ?? "Không tải được đơn gần đây.",
                );
                return of(null);
              }),
            );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((res) => {
        this.recentOrders.set(res?.content ?? []);
        this.totalRecords.set(res?.meta.totalElements ?? 0);
        this.tableLoading.set(false);
      });
  }

  /** Đặt ở đây vì Angular 18 sinh sai mã khi viết `??` ngay trong `track`: build xanh nhưng lỗi lúc chạy. */
  topProductKey(product: DashboardTopProduct): number | string {
    return product.productId ?? product.name;
  }

  loadStats(): void {
    this.statsLoading.set(true);
    this.statsError.set(false);
    this.orderService
      .getOrderStats()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (orderStats) => {
          this.stats.set(this.buildStatCards(orderStats));
          this.statsLoading.set(false);
        },
        error: () => {
          this.statsError.set(true);
          this.statsLoading.set(false);
        },
      });
  }

  reloadOverview(): void {
    this.overviewRequests.next(this.selectedDays());
  }

  onPeriodChange(days: number | null): void {
    if (days === null || days === this.selectedDays()) return;
    this.selectedDays.set(days);
    this.reloadOverview();
  }

  reloadTable(): void {
    this.loadSubject.next({
      page: Math.floor(this.tableFirst() / this.PAGE_SIZE),
      search: this.searchTerm(),
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

  isRecentFirstPage(): boolean {
    return this.tableFirst() === 0;
  }

  isRecentLastPage(): boolean {
    const pc = this.pageCount();
    if (pc < 1) return true;
    return this.tableFirst() >= (pc - 1) * this.PAGE_SIZE;
  }

  goRecentFirst(): void {
    this.goRecentPageIndex(0);
  }

  goRecentPrev(): void {
    const idx = Math.floor(this.tableFirst() / this.PAGE_SIZE) - 1;
    if (idx >= 0) this.goRecentPageIndex(idx);
  }

  goRecentNext(): void {
    const idx = Math.floor(this.tableFirst() / this.PAGE_SIZE) + 1;
    if (idx < this.pageCount()) this.goRecentPageIndex(idx);
  }

  goRecentLast(): void {
    const pc = this.pageCount();
    if (pc > 0) this.goRecentPageIndex(pc - 1);
  }

  goRecentPage(page1Based: number): void {
    this.goRecentPageIndex(page1Based - 1);
  }

  private goRecentPageIndex(pageIndex: number): void {
    const pc = this.pageCount();
    if (pageIndex < 0 || pageIndex >= pc) return;
    const first = pageIndex * this.PAGE_SIZE;
    this.tableFirst.set(first);
    this.loadSubject.next({
      page: pageIndex,
      search: this.searchTerm(),
    });
  }

  exportOrders(): void {
    if (this.exportLoading()) return;
    this.exportLoading.set(true);
    this.orderService
      .getAllOrders({ search: this.searchTerm() || undefined })
      .pipe(
        // Chỉ tạo file khi đã lấy đủ mọi trang, lỗi giữa chừng thì không có file thiếu dòng.
        switchMap((orders) =>
          orders.length === 0
            ? of(0)
            : from(
                this.excelExport.exportOrders(orders, "danh-sach-don-hang"),
              ).pipe(map(() => orders.length)),
        ),
        finalize(() => this.exportLoading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (count) =>
          this.messageService.add(
            count === 0
              ? {
                  severity: "info",
                  summary: "Không có dữ liệu",
                  detail: "Không có đơn hàng nào để xuất.",
                }
              : {
                  severity: "success",
                  summary: "Thành công",
                  detail: `Đã xuất ${count} đơn hàng.`,
                },
          ),
        error: (err) =>
          this.messageService.add({
            severity: "error",
            summary: "Lỗi",
            detail: err?.error?.message ?? "Xuất Excel thất bại, vui lòng thử lại.",
          }),
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
        label: REVENUE_CARD_LABEL,
        value: revenue === null ? "—" : formatVnd(revenue),
        trend: null,
        up: true,
      },
    ];
  }

  private parseAmount(v: number | string | null | undefined): number | null {
    if (v === null || v === undefined || v === "") return null;
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
  }

  private formatCount(n: number): string {
    return Number.isFinite(n) ? String(Math.max(0, Math.trunc(n))) : "0";
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

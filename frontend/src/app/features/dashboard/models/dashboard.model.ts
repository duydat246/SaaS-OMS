export type DashboardCategoryKind = "CATEGORY" | "UNCATEGORIZED" | "OTHER";

/** GET /api/dashboard/overview */
export interface DashboardOverview {
  days: number;
  timezone: string;
  /** Ngày Việt Nam dạng `yyyy-MM-dd`. */
  fromDate: string;
  toDate: string;
  revenueByDay: DashboardRevenueDay[];
  categories: DashboardCategory[];
  topProducts: DashboardTopProduct[];
}

export interface DashboardRevenueDay {
  date: string;
  deliveredRevenue: number;
  openAmount: number;
}

export interface DashboardCategory {
  kind: DashboardCategoryKind;
  /** Null khi `kind` khác `CATEGORY`, frontend tự gắn nhãn. */
  name: string | null;
  amount: number;
  quantity: number;
}

export interface DashboardTopProduct {
  /** Null với item cũ không có SKU. */
  productId: number | null;
  name: string;
  quantity: number;
  amount: number;
}

import { DashboardCategoryKind } from "@features/dashboard/models/dashboard.model";

export interface DashboardPeriodOption {
  label: string;
  value: number;
}

export const DASHBOARD_DEFAULT_DAYS = 7;

export const DASHBOARD_PERIOD_OPTIONS: DashboardPeriodOption[] = [
  { label: "7 ngày", value: 7 },
  { label: "30 ngày", value: 30 },
  { label: "90 ngày", value: 90 },
];

export const DASHBOARD_CATEGORY_KIND_LABEL: Record<
  Exclude<DashboardCategoryKind, "CATEGORY">,
  string
> = {
  UNCATEGORIZED: "Chưa phân loại",
  OTHER: "Khác",
};

// Bảng màu đã chạy kiểm tra phân biệt màu (CVD) trên nền sáng. Hai nhóm không tên dùng xám để lùi xuống.
export const DASHBOARD_DELIVERED_COLOR = "#2563eb";
export const DASHBOARD_OPEN_COLOR = "#c96f00";
export const DASHBOARD_CATEGORY_COLORS = [
  "#2563eb",
  "#c96f00",
  "#0e9f8e",
  "#a23ec7",
  "#d6336c",
];
export const DASHBOARD_NEUTRAL_COLORS: Record<
  Exclude<DashboardCategoryKind, "CATEGORY">,
  string
> = {
  UNCATEGORIZED: "#b8c0cc",
  OTHER: "#7a8594",
};

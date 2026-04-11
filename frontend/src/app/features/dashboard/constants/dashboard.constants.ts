export interface DashboardPeriodOption {
  label: string;
  value: "week" | "month" | "quarter";
}

export interface DashboardTopProduct {
  name: string;
  price: number;
  rating: number;
}

export const DASHBOARD_PERIOD_OPTIONS: DashboardPeriodOption[] = [
  { label: "Tuần trước", value: "week" },
  { label: "Tháng này", value: "month" },
  { label: "Quý này", value: "quarter" },
];

export const DASHBOARD_TOP_PRODUCTS: DashboardTopProduct[] = [
  { name: "Đồng hồ tre", price: 650000, rating: 5 },
  { name: "Tai nghe không dây", price: 1290000, rating: 4 },
  { name: "Bộ sạc nhanh", price: 350000, rating: 5 },
  { name: "Ốp lưng điện thoại", price: 180000, rating: 4 },
];

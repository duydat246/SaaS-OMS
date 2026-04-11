import type { PlatformSource } from "./order.model";

/**
 * Giá trị trùng chuỗi enum backend (PlatformSource).
 */
export enum PlatformSourceCode {
  TIKTOK = "TIKTOK",
  SHOPEE = "SHOPEE",
  MANUAL = "MANUAL",
}

/** Nhãn tiếng Việt hiển thị theo mã nền tảng backend */
export const PLATFORM_SOURCE_LABEL_VI: Record<PlatformSource, string> = {
  TIKTOK: "TikTok Shop",
  SHOPEE: "Shopee",
  MANUAL: "Nhập tay",
};

/** Lấy nhãn tiếng Việt; nếu lạ (API mới) thì trả về mã gốc */
export function platformSourceLabelVi(
  platformSource: string | null | undefined,
): string {
  if (platformSource == null || platformSource === "") return "—";
  return (
    PLATFORM_SOURCE_LABEL_VI[platformSource as PlatformSource] ?? platformSource
  );
}

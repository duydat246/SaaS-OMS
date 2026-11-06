import { HttpErrorResponse } from "@angular/common/http";
import { ActivatedRoute } from "@angular/router";
import { distinctUntilChanged, EMPTY, map, Observable } from "rxjs";
import { formatVnd } from "@shared/utils/format-vnd";

export const STOREFRONT_PAGE_SIZE = 20;
export const STOREFRONT_DEFAULT_SORT = "updatedAt,desc";
export const STOREFRONT_SEARCH_MAX_LENGTH = 100;
export const STOREFRONT_EMPTY_VALUE = "—";

/** Font của trang khách. Cả hai đều có subset `vietnamese`; Urbanist trong thiết kế gốc thì không. */
export const STOREFRONT_FONT_URL =
  "https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600&family=JetBrains+Mono:wght@400;500&display=swap";
export const STOREFRONT_FONT_LINK_ID = "sf-fonts";

export const STOREFRONT_SORT_OPTIONS: ReadonlyArray<{ value: string; label: string }> = [
  { value: STOREFRONT_DEFAULT_SORT, label: "Mới cập nhật" },
  { value: "createdAt,desc", label: "Mới thêm" },
  { value: "name,asc", label: "Tên A đến Z" },
  { value: "name,desc", label: "Tên Z đến A" },
];

const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const RATE_LIMIT_MESSAGE = "Bạn truy cập quá nhanh, vui lòng thử lại sau ít phút";
const NETWORK_MESSAGE = "Không kết nối được tới cửa hàng. Kiểm tra mạng rồi thử lại.";

export function isValidStorefrontSlug(slug: string | null): slug is string {
  return !!slug && slug.length >= 3 && slug.length <= 100 && SLUG_PATTERN.test(slug);
}

/** Route con không kế thừa `:slug`, nên phải đọc từ route cha. */
export function slugFromParent(route: ActivatedRoute): Observable<string> {
  return (route.parent?.paramMap ?? EMPTY).pipe(
    map((params) => params.get("slug") ?? ""),
    distinctUntilChanged(),
  );
}

/** Trang khách không có toast: mọi lỗi hiển thị ngay trên trang bằng câu này. */
export function storefrontErrorMessage(err: unknown, fallback: string): string {
  const response = err instanceof HttpErrorResponse ? err : null;
  const message: unknown = response?.error?.message;
  if (typeof message === "string" && message) {
    return message;
  }
  if (response?.status === 429) {
    return RATE_LIMIT_MESSAGE;
  }
  return response?.status === 0 ? NETWORK_MESSAGE : fallback;
}

export function isNotFoundError(err: unknown): boolean {
  return err instanceof HttpErrorResponse && err.status === 404;
}

export function formatStorefrontPrice(value: number | null): string {
  return value === null ? STOREFRONT_EMPTY_VALUE : formatVnd(value);
}

export function formatStorefrontPriceRange(min: number | null, max: number | null): string {
  if (min === null || max === null || min === max) {
    return formatStorefrontPrice(min ?? max);
  }
  return `${formatVnd(min)} – ${formatVnd(max)}`;
}

/** Ô trên lưới hẹp: giá nhiều mức chỉ hiện mức thấp nhất. */
export function formatStorefrontPriceFrom(min: number | null, max: number | null): string {
  if (min === null || max === null || min === max) {
    return formatStorefrontPrice(min ?? max);
  }
  return `Từ ${formatVnd(min)}`;
}

export function telLink(phone: string): string {
  return `tel:${phone}`;
}

/** Zalo cần số quốc tế không có dấu +: `0901...` thành `84901...`. */
export function zaloLink(phone: string): string {
  const digits = phone.replace(/\D/g, "");
  return `https://zalo.me/${digits.startsWith("0") ? `84${digits.slice(1)}` : digits}`;
}

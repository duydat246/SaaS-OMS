import { Translation } from "primeng/api";

/**
 * Bản dịch cho chuỗi mặc định của PrimeNG.
 * `setTranslation` merge nông: truyền `aria` thẳng vào sẽ mất các khoá khác như `close`,
 * nên phần `aria` tách riêng để nơi gọi spread cùng giá trị đang có.
 */
export const PRIMENG_VI_ARIA: NonNullable<Translation["aria"]> = {
  pageLabel: "Trang {page}",
  firstPageLabel: "Trang đầu",
  prevPageLabel: "Trang trước",
  nextPageLabel: "Trang sau",
  lastPageLabel: "Trang cuối",
  rowsPerPageLabel: "Số dòng mỗi trang",
  jumpToPageDropdownLabel: "Chọn trang",
};

export const PRIMENG_VI: Omit<Translation, "aria"> = {
  emptyMessage: "Không có dữ liệu",
  emptyFilterMessage: "Không tìm thấy kết quả",
  choose: "Chọn",
  upload: "Tải lên",
  cancel: "Huỷ",
};

import { Injectable } from "@angular/core";
import * as ExcelJS from "exceljs";
import { Order } from "@features/orders/models/order.model";
import { orderStatusLabelVi } from "@features/orders/models/order-status";
import { platformSourceLabelVi } from "@features/orders/models/platform-source";
import { Product } from "@features/products/models/product.model";
import { productStatusLabelVi } from "@features/products/models/product-status";

// ── Palette ────────────────────────────────────────────────────────────────
const TITLE_FG = "FF1E293B"; // slate-900
const HEADER_BG = "FF1E3A5F"; // dark navy
const HEADER_FG = "FFFFFFFF"; // white
const ROW_BG_ODD = "FFFFFFFF"; // white
const ROW_BG_EVEN = "FFF1F5F9"; // slate-100
const BORDER_HDR = "FF94A3B8"; // slate-400
const BORDER_ROW = "FFE2E8F0"; // slate-200

// ── Layout ─────────────────────────────────────────────────────────────────
const COL_COUNT = 6;
const TITLE_ROW = 1;
const HEADER_ROW = 5; // 3 empty rows (2-4) between title and header
const DATA_START = HEADER_ROW + 1;

const COL_COUNT_PRODUCT = 6;

const COLUMNS: { header: string; width: number }[] = [
  { header: "Mã đơn", width: 12 },
  { header: "Khách hàng", width: 26 },
  { header: "Nền tảng", width: 16 },
  { header: "Tổng tiền (₫)", width: 18 },
  { header: "Trạng thái", width: 18 },
  { header: "Ngày tạo", width: 14 },
];

const COLUMNS_PRODUCT: { header: string; width: number }[] = [
  { header: "Mã SP", width: 10 },
  { header: "Tên sản phẩm", width: 32 },
  { header: "Danh mục", width: 18 },
  { header: "Số SKU", width: 10 },
  { header: "Tồn kho", width: 12 },
  { header: "Trạng thái", width: 16 },
];

// ── Helpers ────────────────────────────────────────────────────────────────
type BorderStyle = ExcelJS.BorderStyle;
const THIN: BorderStyle = "thin";

function cellBorder(color: string): Partial<ExcelJS.Borders> {
  const side: Partial<ExcelJS.Border> = { style: THIN, color: { argb: color } };
  return { top: side, left: side, bottom: side, right: side };
}

function solidFill(argb: string): ExcelJS.Fill {
  return { type: "pattern", pattern: "solid", fgColor: { argb: argb } };
}

// ── Service ────────────────────────────────────────────────────────────────
@Injectable({ providedIn: "root" })
export class ExcelExportService {
  async exportOrders(orders: Order[], filename = "don-hang"): Promise<void> {
    const wb = new ExcelJS.Workbook();
    const ws = wb.addWorksheet("Đơn hàng");

    // Column widths (no auto-generated header row)
    COLUMNS.forEach((col, i) => {
      ws.getColumn(i + 1).width = col.width;
    });

    // ── Title row ──────────────────────────────────────────────────────────
    ws.mergeCells(TITLE_ROW, 1, TITLE_ROW, COL_COUNT);
    const titleCell = ws.getCell(TITLE_ROW, 1);
    titleCell.value = "Danh sách đơn hàng";
    titleCell.font = { bold: true, size: 14, color: { argb: TITLE_FG } };
    titleCell.alignment = { vertical: "middle", horizontal: "center" };
    ws.getRow(TITLE_ROW).height = 34;

    // ── 3 empty rows (rows 2-4) ────────────────────────────────────────────
    for (let r = TITLE_ROW + 1; r < HEADER_ROW; r++) {
      ws.getRow(r).height = 10;
    }

    // ── Header row ─────────────────────────────────────────────────────────
    const headerRow = ws.getRow(HEADER_ROW);
    headerRow.values = COLUMNS.map((c) => c.header);
    headerRow.height = 26;
    headerRow.eachCell({ includeEmpty: false }, (cell) => {
      cell.fill = solidFill(HEADER_BG);
      cell.font = { bold: true, color: { argb: HEADER_FG }, size: 11 };
      cell.alignment = { vertical: "middle", horizontal: "center" };
      cell.border = cellBorder(BORDER_HDR);
    });

    // ── Data rows ──────────────────────────────────────────────────────────
    orders.forEach((o, i) => {
      const row = ws.getRow(DATA_START + i);
      row.values = [
        `#${o.id}`,
        o.customer?.fullName ?? "—",
        platformSourceLabelVi(o.platformSource),
        o.totalAmount,
        orderStatusLabelVi(o.status),
        o.createdAt ? new Date(o.createdAt).toLocaleDateString("vi-VN") : "—",
      ];
      row.height = 20;
      const bg = i % 2 === 0 ? ROW_BG_ODD : ROW_BG_EVEN;
      row.eachCell({ includeEmpty: false }, (cell) => {
        cell.fill = solidFill(bg);
        cell.alignment = { vertical: "middle" };
        cell.border = cellBorder(BORDER_ROW);
      });
    });

    // ── Download ───────────────────────────────────────────────────────────
    const buffer = await wb.xlsx.writeBuffer();
    const blob = new Blob([buffer], {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${filename}-${this.today()}.xlsx`;
    a.click();
    URL.revokeObjectURL(url);
  }

  async exportProducts(products: Product[], filename = "san-pham"): Promise<void> {
    const wb = new ExcelJS.Workbook();
    const ws = wb.addWorksheet("Sản phẩm");

    // Column widths (no auto-generated header row)
    COLUMNS_PRODUCT.forEach((col, i) => {
      ws.getColumn(i + 1).width = col.width;
    });

    // ── Title row ──────────────────────────────────────────────────────────
    ws.mergeCells(TITLE_ROW, 1, TITLE_ROW, COL_COUNT_PRODUCT);
    const titleCell = ws.getCell(TITLE_ROW, 1);
    titleCell.value = "Danh sách sản phẩm";
    titleCell.font = { bold: true, size: 14, color: { argb: TITLE_FG } };
    titleCell.alignment = { vertical: "middle", horizontal: "center" };
    ws.getRow(TITLE_ROW).height = 34;

    // ── 3 empty rows (rows 2-4) ────────────────────────────────────────────
    for (let r = TITLE_ROW + 1; r < HEADER_ROW; r++) {
      ws.getRow(r).height = 10;
    }

    // ── Header row ─────────────────────────────────────────────────────────
    const headerRow = ws.getRow(HEADER_ROW);
    headerRow.values = COLUMNS_PRODUCT.map((c) => c.header);
    headerRow.height = 26;
    headerRow.eachCell({ includeEmpty: false }, (cell) => {
      cell.fill = solidFill(HEADER_BG);
      cell.font = { bold: true, color: { argb: HEADER_FG }, size: 11 };
      cell.alignment = { vertical: "middle", horizontal: "center" };
      cell.border = cellBorder(BORDER_HDR);
    });

    // ── Data rows ──────────────────────────────────────────────────────────
    products.forEach((p, i) => {
      const row = ws.getRow(DATA_START + i);
      row.values = [
        String(p.id),
        p.name,
        p.category ?? "—",
        p.skuCount,
        p.totalStock,
        productStatusLabelVi(p.status),
      ];
      row.height = 20;
      const bg = i % 2 === 0 ? ROW_BG_ODD : ROW_BG_EVEN;
      row.eachCell({ includeEmpty: false }, (cell) => {
        cell.fill = solidFill(bg);
        cell.alignment = { vertical: "middle" };
        cell.border = cellBorder(BORDER_ROW);
      });
    });

    // ── Download ───────────────────────────────────────────────────────────
    const buffer = await wb.xlsx.writeBuffer();
    const blob = new Blob([buffer], {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${filename}-${this.today()}.xlsx`;
    a.click();
    URL.revokeObjectURL(url);
  }

  private today(): string {
    return new Date().toISOString().slice(0, 10);
  }
}

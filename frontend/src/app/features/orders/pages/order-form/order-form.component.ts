import { Component, DestroyRef, inject, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { HttpErrorResponse } from "@angular/common/http";
import {
  AbstractControl,
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from "@angular/forms";
import { Router, RouterLink } from "@angular/router";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { debounceTime, Subject, switchMap } from "rxjs";
import { MessageService } from "primeng/api";
import {
  AutoCompleteCompleteEvent,
  AutoCompleteModule,
} from "primeng/autocomplete";
import { ButtonModule } from "primeng/button";
import { CardModule } from "primeng/card";
import { DropdownModule } from "primeng/dropdown";
import { InputNumberModule } from "primeng/inputnumber";
import { InputTextModule } from "primeng/inputtext";
import { ProductService } from "@features/products/services/product.service";
import { Product, Sku } from "@features/products/models/product.model";
import {
  CreateOrderItemRequest,
  CreateOrderRequest,
} from "@features/orders/models/order.model";
import { OrderService } from "@features/orders/services/order.service";

type OrderLineForm = FormGroup<{
  product: FormControl<Product | null>;
  sku: FormControl<Sku | null>;
  quantity: FormControl<string>;
}>;

interface VariantOption {
  label: string;
  value: Sku;
  disabled: boolean;
}

interface OrderSummary {
  subtotal: number;
  total: number;
  /** Lỗi của tổng đơn theo cùng quy tắc với backend; null khi hợp lệ. */
  error: string | null;
}

/** Giới hạn của DECIMAL(12,2), tính bằng đồng xu lẻ để tránh sai số dấu phẩy động. */
const ORDER_AMOUNT_LIMIT_CENTS = 999_999_999_999;
/** Ô phí và giảm giá chỉ nhập số nguyên nên trần là phần nguyên của DECIMAL(12,2). */
const MONEY_INPUT_MAX = 9_999_999_999;
const QUANTITY_PATTERN = /^\d+$/;

function toCents(value: number | null | undefined): number {
  return value == null || Number.isNaN(value)
    ? 0
    : Math.round(Number(value) * 100);
}

/**
 * Nguồn duy nhất đọc số lượng từ ô nhập: chỉ nhận chuỗi toàn chữ số, còn lại trả null.
 * Số vượt MAX_SAFE_INTEGER được giữ ở mức trần để vẫn là "quá lớn", không thành Infinity hay bị làm tròn.
 */
function parseQuantity(value: string): number | null {
  const text = value.trim();
  return QUANTITY_PATTERN.test(text)
    ? Math.min(Number(text), Number.MAX_SAFE_INTEGER)
    : null;
}

function quantityValidator(stock: number | null): ValidatorFn {
  return (control: AbstractControl<string>): ValidationErrors | null => {
    const quantity = parseQuantity(control.value);
    if (quantity === null) {
      return control.value.trim() ? { integer: true } : { required: true };
    }
    if (quantity < 1) {
      return { min: true };
    }
    return stock !== null && quantity > stock ? { max: true } : null;
  };
}

@Component({
  selector: "app-order-form",
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    AutoCompleteModule,
    ButtonModule,
    CardModule,
    DropdownModule,
    InputNumberModule,
    InputTextModule,
  ],
  templateUrl: "./order-form.component.html",
  styleUrls: ["./order-form.component.scss"],
})
export class OrderFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly orderService = inject(OrderService);
  private readonly productService = inject(ProductService);
  private readonly router = inject(Router);
  private readonly messageService = inject(MessageService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly productQueries = new Subject<string>();

  readonly form = this.fb.group({
    customerName: this.fb.nonNullable.control("", [
      Validators.required,
      Validators.maxLength(150),
    ]),
    customerPhone: this.fb.nonNullable.control("", [
      Validators.required,
      Validators.maxLength(30),
    ]),
    customerAddress: this.fb.nonNullable.control(""),
    shippingFee: this.fb.control<number | null>(0, [
      Validators.min(0),
      Validators.max(MONEY_INPUT_MAX),
    ]),
    discount: this.fb.control<number | null>(0, [
      Validators.min(0),
      Validators.max(MONEY_INPUT_MAX),
    ]),
    notes: this.fb.nonNullable.control(""),
    items: this.fb.array<OrderLineForm>([this.newLine()]),
  });

  readonly loading = signal(false);
  readonly productSuggestions = signal<Product[]>([]);
  readonly productsLoading = signal(false);
  readonly summary = signal<OrderSummary>({
    subtotal: 0,
    total: 0,
    error: null,
  });
  readonly moneyInputMax = MONEY_INPUT_MAX;

  constructor() {
    this.productQueries
      .pipe(
        debounceTime(250),
        switchMap((query) =>
          this.productService.getProducts({
            status: "ACTIVE",
            search: query || undefined,
            page: 0,
            size: 20,
          }),
        ),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (page) => {
          this.productSuggestions.set(page.content);
          this.productsLoading.set(false);
        },
        error: () => {
          this.productSuggestions.set([]);
          this.productsLoading.set(false);
          this.showError("Không tải được danh sách sản phẩm.");
        },
      });

    this.form.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.recalculate());

    this.recalculate();
  }

  get items(): FormArray<OrderLineForm> {
    return this.form.controls.items;
  }

  searchProducts(event: AutoCompleteCompleteEvent): void {
    this.productsLoading.set(true);
    this.productQueries.next((event.query ?? "").trim());
  }

  variantOptions(line: OrderLineForm): VariantOption[] {
    const product = line.controls.product.value;
    if (!product) {
      return [];
    }

    return product.skus.map((sku) => ({
      label: `${sku.variant?.trim() || "Mặc định"} · ${sku.skuCode} · còn ${sku.stock}`,
      value: sku,
      disabled: sku.stock <= 0,
    }));
  }

  maxQuantity(line: OrderLineForm): number {
    return line.controls.sku.value?.stock ?? 1;
  }

  clampQuantity(line: OrderLineForm): void {
    const sku = line.controls.sku.value;
    const quantity = parseQuantity(line.controls.quantity.value);
    // Giá trị không hợp lệ giữ nguyên để hiện lỗi, không tự đổi thành số khác.
    if (!sku || quantity === null || quantity < 1) {
      return;
    }

    const clamped = Math.min(quantity, Math.max(sku.stock, 1));
    if (clamped !== quantity) {
      line.controls.quantity.setValue(String(clamped));
      this.showWarn(
        `Số lượng đã được điều chỉnh theo tồn kho (${sku.stock}).`,
      );
    }
  }

  quantityError(line: OrderLineForm): string | null {
    const quantity = line.controls.quantity;
    if (quantity.valid || !(quantity.dirty || quantity.touched)) {
      return null;
    }
    if (quantity.hasError("required")) {
      return "Vui lòng nhập số lượng là số nguyên.";
    }
    if (quantity.hasError("integer")) {
      return "Số lượng phải là số nguyên dương, chỉ gồm chữ số.";
    }
    return line.controls.sku.value
      ? `Số lượng phải từ 1 đến ${this.maxQuantity(line)}.`
      : "Số lượng tối thiểu là 1.";
  }

  lineTotal(line: OrderLineForm): number {
    return this.lineCents(line) / 100;
  }

  addItem(): void {
    this.items.push(this.newLine());
  }

  removeItem(index: number): void {
    if (this.items.length === 1) {
      return;
    }
    this.items.removeAt(index);
  }

  onSubmit(): void {
    if (this.loading()) {
      return;
    }

    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.showWarn("Vui lòng kiểm tra lại khách hàng và sản phẩm.");
      return;
    }

    const summaryError = this.summary().error;
    if (summaryError) {
      this.showWarn(summaryError);
      return;
    }

    const seenSkuIds = new Set<number>();
    const items: CreateOrderItemRequest[] = [];

    for (const line of this.items.controls) {
      const sku = line.controls.sku.value;
      const quantity = parseQuantity(line.controls.quantity.value);
      if (!sku || quantity === null) {
        continue;
      }

      if (seenSkuIds.has(sku.id)) {
        this.showWarn("Mỗi biến thể chỉ được chọn một lần trong đơn hàng.");
        return;
      }

      if (quantity > sku.stock) {
        this.showWarn(
          `Biến thể "${sku.variant ?? sku.skuCode}" chỉ còn ${sku.stock} trong kho.`,
        );
        return;
      }

      seenSkuIds.add(sku.id);
      items.push({ skuId: sku.id, quantity });
    }

    if (items.length === 0) {
      this.showWarn("Vui lòng chọn ít nhất một sản phẩm.");
      return;
    }

    const values = this.form.getRawValue();
    const request: CreateOrderRequest = {
      customerName: values.customerName.trim(),
      customerPhone: values.customerPhone.trim(),
      customerAddress: values.customerAddress.trim(),
      shippingFee: this.amount(values.shippingFee),
      discount: this.amount(values.discount),
      notes: values.notes.trim(),
      items,
    };

    this.loading.set(true);
    this.orderService
      .createOrder(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (order) => {
          this.messageService.add({
            severity: "success",
            summary: "Thành công",
            detail:
              "Đã tạo đơn hàng. Tồn kho sẽ được trừ khi đơn được xác nhận.",
          });
          this.router.navigate(["/orders", order.id]);
        },
        error: (err: HttpErrorResponse) => {
          this.loading.set(false);
          this.showError(err.error?.message ?? "Tạo đơn hàng thất bại.");
        },
      });
  }

  private newLine(): OrderLineForm {
    const line = this.fb.group({
      product: this.fb.control<Product | null>(null, {
        validators: [Validators.required],
      }),
      sku: this.fb.control<Sku | null>(null, {
        validators: [Validators.required],
      }),
      quantity: this.fb.nonNullable.control("1", {
        validators: [quantityValidator(null)],
      }),
    });

    line.controls.product.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        line.controls.sku.reset(null, { emitEvent: false });
        this.applyStockValidators(line);
      });

    line.controls.sku.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.applyStockValidators(line));

    return line;
  }

  private applyStockValidators(line: OrderLineForm): void {
    const sku = line.controls.sku.value;
    const quantity = line.controls.quantity;
    quantity.setValidators(quantityValidator(sku?.stock ?? null));

    const current = parseQuantity(quantity.value);
    if (sku && current !== null && current >= 1) {
      const clamped = Math.min(current, Math.max(sku.stock, 1));
      if (clamped !== current) {
        quantity.setValue(String(clamped));
        this.showWarn(`Số lượng đã được điều chỉnh theo tồn kho (${sku.stock}).`);
      }
    }

    quantity.updateValueAndValidity();
  }

  private recalculate(): void {
    const lineCents = this.items.controls.map((line) => this.lineCents(line));
    const subtotalCents = lineCents.reduce((sum, cents) => sum + cents, 0);
    const shippingFeeCents = toCents(this.form.controls.shippingFee.value);
    const discountCents = toCents(this.form.controls.discount.value);
    const totalCents = subtotalCents + shippingFeeCents - discountCents;
    const overLimit =
      subtotalCents > ORDER_AMOUNT_LIMIT_CENTS ||
      totalCents > ORDER_AMOUNT_LIMIT_CENTS ||
      lineCents.some((cents) => cents > ORDER_AMOUNT_LIMIT_CENTS);

    let error: string | null = null;
    if (overLimit) {
      error =
        "Giá trị đơn hàng vượt giới hạn 9.999.999.999,99 ₫, vui lòng giảm số lượng.";
    } else if (totalCents < 0 && this.allLinesComplete()) {
      // Dòng chưa chọn SKU hoặc số lượng đang lỗi làm tạm tính thấp hơn thực tế,
      // nên chưa thể kết luận giảm giá sai; lỗi đã hiện ngay tại dòng đó.
      error = "Giảm giá không được lớn hơn tạm tính cộng phí vận chuyển.";
    }

    this.summary.set({
      subtotal: subtotalCents / 100,
      total: totalCents / 100,
      error,
    });
  }

  private allLinesComplete(): boolean {
    return this.items.controls.every(
      (line) => line.controls.sku.value && line.controls.quantity.valid,
    );
  }

  /** Giống backend: đơn giá nhân số lượng, tính bằng đồng xu lẻ. */
  private lineCents(line: OrderLineForm): number {
    const sku = line.controls.sku.value;
    const quantity = parseQuantity(line.controls.quantity.value);
    return sku && quantity !== null ? toCents(sku.price) * quantity : 0;
  }

  private amount(value: number | null | undefined): number {
    return value == null || Number.isNaN(value) ? 0 : Number(value);
  }

  private showWarn(detail: string): void {
    this.messageService.add({
      severity: "warn",
      summary: "Chưa hợp lệ",
      detail,
    });
  }

  private showError(detail: string): void {
    this.messageService.add({ severity: "error", summary: "Lỗi", detail });
  }
}

import { Component, DestroyRef, inject, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { HttpErrorResponse } from "@angular/common/http";
import {
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
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
  quantity: FormControl<number | null>;
}>;

interface VariantOption {
  label: string;
  value: Sku;
  disabled: boolean;
}

interface OrderSummary {
  subtotal: number;
  total: number;
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
    shippingFee: this.fb.control<number | null>(0, [Validators.min(0)]),
    discount: this.fb.control<number | null>(0, [Validators.min(0)]),
    notes: this.fb.nonNullable.control(""),
    items: this.fb.array<OrderLineForm>([this.newLine()]),
  });

  readonly loading = signal(false);
  readonly productSuggestions = signal<Product[]>([]);
  readonly productsLoading = signal(false);
  readonly summary = signal<OrderSummary>({ subtotal: 0, total: 0 });

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
    const quantity = line.controls.quantity.value;
    if (!sku || quantity == null) {
      return;
    }

    const clamped = Math.min(Math.max(quantity, 1), Math.max(sku.stock, 1));
    if (clamped !== quantity) {
      line.controls.quantity.setValue(clamped);
      this.showWarn(
        `Số lượng đã được điều chỉnh theo tồn kho (${sku.stock}).`,
      );
    }
  }

  lineTotal(line: OrderLineForm): number {
    const sku = line.controls.sku.value;
    const quantity = line.controls.quantity.value ?? 0;
    return sku ? this.amount(sku.price) * quantity : 0;
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

    const seenSkuIds = new Set<number>();
    const items: CreateOrderItemRequest[] = [];

    for (const line of this.items.controls) {
      const sku = line.controls.sku.value;
      const quantity = line.controls.quantity.value;
      if (!sku || quantity == null) {
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
      quantity: this.fb.control<number | null>(1, {
        validators: [Validators.required, Validators.min(1)],
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
    const validators = [Validators.required, Validators.min(1)];

    if (sku) {
      validators.push(Validators.max(sku.stock));
    }
    quantity.setValidators(validators);

    if (sku) {
      const current = quantity.value ?? 1;
      const clamped = Math.min(Math.max(current, 1), Math.max(sku.stock, 1));
      if (clamped !== current) {
        quantity.setValue(clamped);
      }
    }

    quantity.updateValueAndValidity();
  }

  private recalculate(): void {
    const subtotal = this.items.controls.reduce(
      (sum, line) => sum + this.lineTotal(line),
      0,
    );
    const shippingFee = this.amount(this.form.controls.shippingFee.value);
    const discount = this.amount(this.form.controls.discount.value);

    this.summary.set({
      subtotal,
      total: Math.max(0, subtotal + shippingFee - discount),
    });
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

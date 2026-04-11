import { Component, inject, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import {
  ReactiveFormsModule,
  FormBuilder,
  FormArray,
  Validators,
} from "@angular/forms";
import { Router, RouterLink } from "@angular/router";
import { CardModule } from "primeng/card";
import { ButtonModule } from "primeng/button";
import { InputTextModule } from "primeng/inputtext";
import { InputNumberModule } from "primeng/inputnumber";
import { MessageService } from "primeng/api";
import { OrderService } from "@features/orders/services/order.service";

@Component({
  selector: "app-order-form",
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    CardModule,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
  ],
  template: `
    <div class="flex flex-column gap-3">
      <!-- Header -->
      <div class="flex align-items-center gap-2">
        <a routerLink="/orders">
          <p-button
            icon="pi pi-arrow-left"
            [rounded]="true"
            [text]="true"
            severity="secondary"
          />
        </a>
        <h2 class="m-0">Tạo đơn hàng mới</h2>
      </div>

      <form [formGroup]="form" (ngSubmit)="onSubmit()">
        <div class="grid">
          <!-- Customer Info -->
          <div class="col-12 md:col-6">
            <p-card header="Khách hàng">
              <div class="flex flex-column gap-3">
                <div class="flex flex-column gap-1">
                  <label class="font-medium">Họ và tên *</label>
                  <input
                    pInputText
                    formControlName="customerName"
                    placeholder="Nguyễn Văn A"
                    class="w-full"
                  />
                </div>
                <div class="flex flex-column gap-1">
                  <label class="font-medium">Số điện thoại *</label>
                  <input
                    pInputText
                    formControlName="customerPhone"
                    placeholder="0901234567"
                    class="w-full"
                  />
                </div>
                <div class="flex flex-column gap-1">
                  <label class="font-medium">Địa chỉ giao hàng</label>
                  <input
                    pInputText
                    formControlName="customerAddress"
                    placeholder="123 Đường, Thành phố"
                    class="w-full"
                  />
                </div>
                <div class="flex flex-column gap-1">
                  <label class="font-medium">Ghi chú</label>
                  <input
                    pInputText
                    formControlName="notes"
                    placeholder="Yêu cầu đặc biệt..."
                    class="w-full"
                  />
                </div>
              </div>
            </p-card>
          </div>

          <!-- Pricing -->
          <div class="col-12 md:col-6">
            <p-card header="Thanh toán">
              <div class="flex flex-column gap-3">
                <div class="flex flex-column gap-1">
                  <label class="font-medium">Phí vận chuyển (₫)</label>
                  <p-inputNumber
                    formControlName="shippingFee"
                    [min]="0"
                    mode="decimal"
                    class="w-full"
                    inputStyleClass="w-full"
                  />
                </div>
                <div class="flex flex-column gap-1">
                  <label class="font-medium">Giảm giá (₫)</label>
                  <p-inputNumber
                    formControlName="discount"
                    [min]="0"
                    mode="decimal"
                    class="w-full"
                    inputStyleClass="w-full"
                  />
                </div>
              </div>
            </p-card>
          </div>

          <!-- Order Items -->
          <div class="col-12">
            <p-card header="Sản phẩm">
              <div class="flex flex-column gap-3" formArrayName="items">
                @for (item of items.controls; track $index; let i = $index) {
                  <div [formGroupName]="i" class="grid align-items-end">
                    <div class="col-12 md:col-4">
                      <label class="font-medium text-sm">Tên sản phẩm *</label>
                      <input
                        pInputText
                        formControlName="productName"
                        placeholder="Áo thun"
                        class="w-full mt-1"
                      />
                    </div>
                    <div class="col-12 md:col-2">
                      <label class="font-medium text-sm">Phân loại</label>
                      <input
                        pInputText
                        formControlName="variant"
                        placeholder="Đỏ / L"
                        class="w-full mt-1"
                      />
                    </div>
                    <div class="col-12 md:col-2">
                      <label class="font-medium text-sm">Số lượng *</label>
                      <p-inputNumber
                        formControlName="quantity"
                        [min]="1"
                        class="w-full mt-1"
                        inputStyleClass="w-full"
                      />
                    </div>
                    <div class="col-12 md:col-3">
                      <label class="font-medium text-sm">Đơn giá (₫) *</label>
                      <p-inputNumber
                        formControlName="unitPrice"
                        [min]="0"
                        mode="decimal"
                        class="w-full mt-1"
                        inputStyleClass="w-full"
                      />
                    </div>
                    <div class="col-12 md:col-1 flex justify-content-end">
                      <p-button
                        icon="pi pi-trash"
                        [rounded]="true"
                        [text]="true"
                        severity="danger"
                        (click)="removeItem(i)"
                        [disabled]="items.length === 1"
                      />
                    </div>
                  </div>
                }
              </div>

              <div class="mt-3">
                <p-button
                  label="Thêm sản phẩm"
                  icon="pi pi-plus"
                  severity="secondary"
                  size="small"
                  (click)="addItem()"
                />
              </div>
            </p-card>
          </div>
        </div>

        <div class="flex gap-2 justify-content-end mt-2">
          <a routerLink="/orders">
            <p-button label="Huỷ" severity="secondary" [outlined]="true" />
          </a>
          <p-button
            type="submit"
            label="Tạo đơn hàng"
            icon="pi pi-check"
            [loading]="loading()"
            [disabled]="form.invalid"
          />
        </div>
      </form>
    </div>
  `,
})
export class OrderFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly orderService = inject(OrderService);
  private readonly router = inject(Router);
  private readonly messageService = inject(MessageService);

  form = this.fb.group({
    customerName: ["", Validators.required],
    customerPhone: ["", Validators.required],
    customerAddress: [""],
    shippingFee: [0],
    discount: [0],
    notes: [""],
    items: this.fb.array([this.newItemGroup()]),
  });

  loading = signal(false);

  get items(): FormArray {
    return this.form.get("items") as FormArray;
  }

  newItemGroup() {
    return this.fb.group({
      productName: ["", Validators.required],
      variant: [""],
      quantity: [1, [Validators.required, Validators.min(1)]],
      unitPrice: [0, [Validators.required, Validators.min(0)]],
    });
  }

  addItem(): void {
    this.items.push(this.newItemGroup());
  }

  removeItem(i: number): void {
    this.items.removeAt(i);
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading.set(true);

    this.orderService.createOrder(this.form.value as any).subscribe({
      next: (order) => {
        this.messageService.add({
          severity: "success",
          summary: "Thành công",
          detail: "Đã tạo đơn hàng mới.",
        });
        this.router.navigate(["/orders", order.id]);
      },
      error: (err) => {
        const detail = err.error?.message ?? "Tạo đơn hàng thất bại.";
        this.messageService.add({ severity: "error", summary: "Lỗi", detail });
        this.loading.set(false);
      },
    });
  }
}

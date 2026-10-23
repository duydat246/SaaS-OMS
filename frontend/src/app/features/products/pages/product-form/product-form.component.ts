import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import {
  AbstractControl,
  FormArray,
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from "@angular/forms";
import { ActivatedRoute, Router, RouterLink } from "@angular/router";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { CardModule } from "primeng/card";
import { ButtonModule } from "primeng/button";
import { InputTextModule } from "primeng/inputtext";
import { InputNumberModule } from "primeng/inputnumber";
import { DropdownModule } from "primeng/dropdown";
import { InputTextareaModule } from "primeng/inputtextarea";
import { ImageModule } from "primeng/image";
import { MessageService } from "primeng/api";
import { ProductService } from "@features/products/services/product.service";
import { Product, ProductRequest, SkuRequest } from "@features/products/models/product.model";
import { PRODUCT_STATUS_OPTIONS } from "@features/products/constants/product.constants";

type PlatformSourceOption = {
  label: string;
  value: string;
};

@Component({
  selector: "app-product-form",
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    CardModule,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    DropdownModule,
    InputTextareaModule,
    ImageModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: "./product-form.component.html",
  styleUrls: ["./product-form.component.scss"],
})
export class ProductFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly productService = inject(ProductService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly messageService = inject(MessageService);

  loading = signal(false);
  product = signal<Product | null>(null);

  isEdit = signal(false);
  productId = signal<number | null>(null);

  platformSourceOptions: PlatformSourceOption[] = [
    { label: "Thủ công", value: "MANUAL" },
    { label: "TikTok", value: "TIKTOK" },
    { label: "Shopee", value: "SHOPEE" },
  ];

  statusOptions = PRODUCT_STATUS_OPTIONS;

  form = this.fb.group({
    name: ["", [Validators.required]],
    description: [""],
    category: [""],
    platformSource: ["MANUAL", [Validators.required]],
    imageUrl: [""],
    status: ["ACTIVE"],
    skus: this.fb.array([this.createSkuGroup()], [this.skuArrayValidator()]),
  });

  skuCount = computed(() => this.skus.length);
  totalStock = computed(() => {
    let sum = 0;
    for (let i = 0; i < this.skus.length; i++) {
      const ctrl = this.skus.at(i);
      const val = ctrl.get("stock")?.value;
      if (typeof val === "number" && !isNaN(val)) {
        sum += val;
      }
    }
    return sum;
  });
  minPrice = computed(() => {
    const prices: number[] = [];
    for (let i = 0; i < this.skus.length; i++) {
      const ctrl = this.skus.at(i);
      const val = ctrl.get("price")?.value;
      if (typeof val === "number" && !isNaN(val)) {
        prices.push(val);
      }
    }
    if (prices.length === 0) return null;
    return Math.min(...prices);
  });
  maxPrice = computed(() => {
    const prices: number[] = [];
    for (let i = 0; i < this.skus.length; i++) {
      const ctrl = this.skus.at(i);
      const val = ctrl.get("price")?.value;
      if (typeof val === "number" && !isNaN(val)) {
        prices.push(val);
      }
    }
    if (prices.length === 0) return null;
    return Math.max(...prices);
  });

  get skus(): FormArray {
    return this.form.get("skus") as FormArray;
  }

  constructor() {
    this.route.paramMap
      .pipe(takeUntilDestroyed())
      .subscribe((params) => {
        const idParam = params.get("id");
        if (idParam && !isNaN(Number(idParam))) {
          const id = Number(idParam);
          this.isEdit.set(true);
          this.productId.set(id);
          this.loadProduct(id);
        } else {
          this.isEdit.set(false);
          this.productId.set(null);
          this.product.set(null);
          // ensure at least one SKU
          if (this.skus.length === 0) {
            this.skus.push(this.createSkuGroup());
          }
        }
      });

    // Update array validator when values change
    effect(() => {
      // trigger revalidation
      this.skus.updateValueAndValidity({ onlySelf: false, emitEvent: false });
    });
  }

  createSkuGroup(id?: number): FormGroup {
    return this.fb.group({
      id: [id ?? null],
      skuCode: ["", [Validators.required]],
      variant: [""],
      price: [0, [Validators.required, Validators.min(0)]],
      stock: [0, [Validators.required, Validators.min(0)]],
    });
  }

  skuArrayValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const arr = control as FormArray;
      if (!arr || arr.length === 0) {
        return { requiredSku: true };
      }
      const codes = arr.controls
        .map((c) => (c.get("skuCode")?.value ?? "").toString().toLowerCase().trim())
        .filter((x) => x.length > 0);
      const seen = new Set<string>();
      for (let i = 0; i < codes.length; i++) {
        if (seen.has(codes[i])) {
          return { duplicateSku: true };
        }
        seen.add(codes[i]);
      }
      return null;
    };
  }

  addSku(): void {
    this.skus.push(this.createSkuGroup());
    this.skus.updateValueAndValidity();
  }

  removeSku(index: number): void {
    if (this.skus.length <= 1) {
      return;
    }
    this.skus.removeAt(index);
    this.skus.updateValueAndValidity();
  }

  loadProduct(id: number): void {
    this.loading.set(true);
    this.productService.getProduct(id).subscribe({
      next: (product) => {
        this.product.set(product);
        this.form.patchValue({
          name: product.name,
          description: product.description ?? "",
          category: product.category ?? "",
          platformSource: product.platformSource ?? "MANUAL",
          imageUrl: product.imageUrl ?? "",
          status: product.status,
        });
        // rebuild skus
        this.skus.clear();
        if (product.skus && product.skus.length > 0) {
          for (const sku of product.skus) {
            this.skus.push(
              this.fb.group({
                id: [sku.id],
                skuCode: [sku.skuCode, [Validators.required]],
                variant: [sku.variant ?? ""],
                price: [sku.price, [Validators.required, Validators.min(0)]],
                stock: [sku.stock, [Validators.required, Validators.min(0)]],
              }),
            );
          }
        } else {
          this.skus.push(this.createSkuGroup());
        }
        this.skus.updateValueAndValidity();
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        const detail = err?.error?.message ?? "Không tải được thông tin sản phẩm.";
        this.messageService.add({ severity: "error", summary: "Lỗi", detail });
      },
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.skus.markAllAsTouched();
      this.skus.updateValueAndValidity();
      return;
    }

    this.loading.set(true);

    const formValue = this.form.getRawValue();
    const skus: SkuRequest[] = (formValue.skus || []).map((s: any) => ({
      id: s.id ?? undefined,
      skuCode: (s.skuCode || "").toString().trim(),
      variant: s.variant && s.variant.toString().trim() !== "" ? s.variant.toString().trim() : null,
      price: Number(s.price ?? 0),
      stock: Number(s.stock ?? 0),
    }));

    const req: ProductRequest = {
      name: (formValue.name || "").toString().trim(),
      description: formValue.description && formValue.description.toString().trim() !== "" ? formValue.description.toString().trim() : null,
      category: formValue.category && formValue.category.toString().trim() !== "" ? formValue.category.toString().trim() : null,
      platformSource: formValue.platformSource || "MANUAL",
      imageUrl: formValue.imageUrl && formValue.imageUrl.toString().trim() !== "" ? formValue.imageUrl.toString().trim() : null,
      skus,
    };

    if (this.isEdit() && this.productId()) {
      this.productService.updateProduct(this.productId()!, req).subscribe({
        next: (product) => {
          this.messageService.add({
            severity: "success",
            summary: "Thành công",
            detail: "Đã cập nhật sản phẩm.",
          });
          this.router.navigate(["/products", product.id]);
        },
        error: (err) => {
          this.loading.set(false);
          const detail = err?.error?.message ?? "Cập nhật sản phẩm thất bại.";
          this.messageService.add({ severity: "error", summary: "Lỗi", detail });
        },
      });
    } else {
      this.productService.createProduct(req).subscribe({
        next: (product) => {
          this.messageService.add({
            severity: "success",
            summary: "Thành công",
            detail: "Đã tạo sản phẩm mới.",
          });
          this.router.navigate(["/products", product.id]);
        },
        error: (err) => {
          this.loading.set(false);
          const detail = err?.error?.message ?? "Tạo sản phẩm thất bại.";
          this.messageService.add({ severity: "error", summary: "Lỗi", detail });
        },
      });
    }
  }

  getSkuGroup(index: number): FormGroup {
    return this.skus.at(index) as FormGroup;
  }

  formatPrice(value: number | null): string {
    if (value === null || value === undefined) return "—";
    return new Intl.NumberFormat("vi-VN").format(value) + "₫";
  }
}

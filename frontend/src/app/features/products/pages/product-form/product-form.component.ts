import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
  viewChild,
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
import { catchError, concatMap, EMPTY, finalize, from, Observable, startWith, tap } from "rxjs";
import { CardModule } from "primeng/card";
import { ButtonModule } from "primeng/button";
import { InputTextModule } from "primeng/inputtext";
import { InputNumberModule } from "primeng/inputnumber";
import { DropdownModule } from "primeng/dropdown";
import { InputTextareaModule } from "primeng/inputtextarea";
import { FileUpload, FileUploadHandlerEvent, FileUploadModule } from "primeng/fileupload";
import { ConfirmDialogModule } from "primeng/confirmdialog";
import { TooltipModule } from "primeng/tooltip";
import { ConfirmationService, MessageService } from "primeng/api";
import { ProductService } from "@features/products/services/product.service";
import { ProductImageService } from "@features/products/services/product-image.service";
import { ProductListStateService } from "@features/products/services/product-list-state.service";
import { Product, ProductImage, ProductRequest, Sku, SkuRequest } from "@features/products/models/product.model";
import { PRODUCT_PLATFORM_OPTIONS, formatVnd } from "@features/products/constants/product.constants";

type SkuFormValue = {
  id: number | null;
  skuCode: string;
  variant: string;
  price: number | null;
  stock: number | null;
};

// Khớp với ràng buộc của ProductRequest và SkuRequest ở backend.
const NAME_MAX_LENGTH = 200;
const DESCRIPTION_MAX_LENGTH = 10000;
const CATEGORY_MAX_LENGTH = 100;
const SKU_CODE_MAX_LENGTH = 100;
const VARIANT_MAX_LENGTH = 200;
const PRICE_MAX = 9999999999.99;
const STOCK_MAX = 2147483647;

const MAX_IMAGES = 10;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const ACCEPTED_IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp", "image/gif"];

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
    FileUploadModule,
    ConfirmDialogModule,
    TooltipModule,
  ],
  providers: [ConfirmationService],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: "./product-form.component.html",
  styleUrls: ["./product-form.component.scss"],
})
export class ProductFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly productService = inject(ProductService);
  private readonly productImageService = inject(ProductImageService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly destroyRef = inject(DestroyRef);
  readonly listQuery = inject(ProductListStateService).lastQuery;

  private readonly imageUploader = viewChild<FileUpload>("imageUploader");

  loading = signal(false);
  saving = signal(false);
  /** Gợi ý danh mục đang dùng, tránh gõ lệch tên tạo ra danh mục trùng. */
  categories = signal<string[]>([]);
  product = signal<Product | null>(null);

  isEdit = signal(false);
  productId = signal<number | null>(null);

  /** Danh sách ảnh theo đúng thứ tự hiển thị = thứ tự gửi lên backend. */
  images = signal<ProductImage[]>([]);
  uploading = signal(false);
  primaryImageId = signal<number | null>(null);
  private readonly brokenImageIds = signal<ReadonlySet<number>>(new Set());
  /** Danh sách ảnh không nằm trong form nên phải tự theo dõi thay đổi. */
  private imagesChanged = false;
  private saved = false;

  readonly maxImages = MAX_IMAGES;
  readonly maxImageSizeMb = MAX_IMAGE_BYTES / (1024 * 1024);
  readonly imageCount = computed(() => this.images().length);
  readonly isImageLimitReached = computed(() => this.images().length >= MAX_IMAGES);

  platformSourceOptions = PRODUCT_PLATFORM_OPTIONS;

  readonly priceMax = PRICE_MAX;
  readonly priceMaxLabel = formatVnd(PRICE_MAX);
  readonly stockMax = STOCK_MAX;
  readonly nameMaxLength = NAME_MAX_LENGTH;
  readonly descriptionMaxLength = DESCRIPTION_MAX_LENGTH;
  readonly categoryMaxLength = CATEGORY_MAX_LENGTH;
  readonly skuCodeMaxLength = SKU_CODE_MAX_LENGTH;
  readonly variantMaxLength = VARIANT_MAX_LENGTH;

  form = this.fb.group({
    name: ["", [Validators.required, Validators.pattern(/\S/), Validators.maxLength(NAME_MAX_LENGTH)]],
    description: ["", [Validators.maxLength(DESCRIPTION_MAX_LENGTH)]],
    category: ["", [Validators.maxLength(CATEGORY_MAX_LENGTH)]],
    platformSource: ["MANUAL", [Validators.required]],
    skus: this.fb.array([this.createSkuGroup()], [this.skuArrayValidator()]),
  });

  /** Ảnh chụp giá trị SKU, cập nhật mỗi khi form đổi để các computed bên dưới phản ứng. */
  private readonly skuRows = signal<SkuFormValue[]>([]);

  skuCount = computed(() => this.skuRows().length);
  totalStock = computed(() =>
    this.skuRows().reduce((sum, sku) => {
      const stock = sku.stock;
      return typeof stock === "number" && Number.isFinite(stock) ? sum + stock : sum;
    }, 0),
  );
  minPrice = computed(() => {
    const prices = this.collectPrices();
    return prices.length === 0 ? null : Math.min(...prices);
  });
  maxPrice = computed(() => {
    const prices = this.collectPrices();
    return prices.length === 0 ? null : Math.max(...prices);
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
          this.images.set([]);
          this.primaryImageId.set(null);
          // ensure at least one SKU
          if (this.skus.length === 0) {
            this.skus.push(this.createSkuGroup());
          }
        }
      });

    // Đồng bộ giá trị SKU vào signal để phần "Tổng hợp" cập nhật theo form.
    this.skus.valueChanges
      .pipe(
        startWith(this.skus.getRawValue() as SkuFormValue[]),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((rows) => this.skuRows.set(rows as SkuFormValue[]));

    this.productService
      .getCategories()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (categories) => this.categories.set(categories ?? []),
        // Chỉ là gợi ý nhập liệu: lỗi thì vẫn gõ tay được.
        error: () => this.categories.set([]),
      });
  }

  /** Guard của route: hỏi lại trước khi rời trang nếu còn thay đổi chưa lưu. */
  canDeactivate(): boolean | Observable<boolean> {
    if (this.saved || !(this.form.dirty || this.imagesChanged)) {
      return true;
    }
    return new Observable<boolean>((subscriber) => {
      const finish = (leave: boolean) => {
        subscriber.next(leave);
        subscriber.complete();
      };
      this.confirmationService.confirm({
        message: "Thay đổi chưa được lưu sẽ bị mất. Bạn có chắc muốn rời trang?",
        header: "Rời trang",
        icon: "pi pi-exclamation-triangle",
        acceptLabel: "Rời trang",
        rejectLabel: "Ở lại",
        acceptButtonStyleClass: "p-button-danger",
        accept: () => finish(true),
        reject: () => finish(false),
      });
    });
  }

  private collectPrices(): number[] {
    return this.skuRows()
      .map((sku) => sku.price)
      .filter(
        (price): price is number =>
          typeof price === "number" && Number.isFinite(price),
      );
  }

  createSkuGroup(sku?: Sku): FormGroup {
    return this.fb.group({
      id: [sku?.id ?? null],
      // Không bắt buộc: để trống thì backend tự sinh mã (SKU mới) hoặc giữ mã cũ (SKU đã có).
      skuCode: [sku?.skuCode ?? "", [Validators.maxLength(SKU_CODE_MAX_LENGTH)]],
      variant: [sku?.variant ?? "", [Validators.maxLength(VARIANT_MAX_LENGTH)]],
      price: [sku?.price ?? 0, [Validators.required, Validators.min(0), Validators.max(PRICE_MAX)]],
      stock: [sku?.stock ?? 0, [Validators.required, Validators.min(0), Validators.max(STOCK_MAX)]],
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
    this.skus.markAsDirty();
    this.skus.updateValueAndValidity();
  }

  removeSku(index: number): void {
    if (this.skus.length <= 1) {
      return;
    }
    this.skus.removeAt(index);
    this.skus.markAsDirty();
    this.skus.updateValueAndValidity();
  }

  loadProduct(id: number): void {
    this.loading.set(true);
    this.productService
      .getProduct(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
      next: (product) => {
        // Backend từ chối sửa sản phẩm đã xoá: đưa về trang chi tiết để khôi phục.
        if (product.status === "ARCHIVED") {
          this.messageService.add({
            severity: "warn",
            summary: "Sản phẩm đã xoá",
            detail: "Hãy khôi phục sản phẩm trước khi sửa.",
          });
          this.router.navigate(["/products", product.id]);
          return;
        }
        this.product.set(product);
        this.form.patchValue({
          name: product.name,
          description: product.description ?? "",
          category: product.category ?? "",
          platformSource: product.platformSource ?? "MANUAL",
        });
        this.applyProductImages(product);
        // rebuild skus
        this.skus.clear();
        if (product.skus && product.skus.length > 0) {
          for (const sku of product.skus) {
            this.skus.push(this.createSkuGroup(sku));
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
        // Không để lại form trống: bấm lưu lúc này sẽ ghi đè bằng dữ liệu rỗng.
        this.router.navigate(["/products"], { queryParams: this.listQuery() });
      },
    });
  }

  private applyProductImages(product: Product): void {
    const images = product.images ?? [];
    this.images.set(images);
    this.brokenImageIds.set(new Set<number>());
    const primary = images.find((image) => image.primary);
    this.primaryImageId.set(primary ? primary.id : (images[0]?.id ?? null));
  }

  // --- Ảnh sản phẩm -------------------------------------------------------

  onUpload(event: FileUploadHandlerEvent): void {
    const selectedFiles: File[] = [...(event.files ?? [])];
    // p-fileupload giữ file trong hàng đợi sau khi emit → xoá để chọn tiếp được.
    this.imageUploader()?.clear();

    if (selectedFiles.length === 0) {
      return;
    }
    if (this.uploading()) {
      this.messageService.add({
        severity: "warn",
        summary: "Đang tải ảnh",
        detail: "Vui lòng đợi quá trình tải ảnh hiện tại hoàn tất.",
      });
      return;
    }

    const remaining = MAX_IMAGES - this.images().length;
    if (remaining <= 0) {
      this.messageService.add({
        severity: "warn",
        summary: "Đã đạt giới hạn",
        detail: `Mỗi sản phẩm chỉ được tối đa ${MAX_IMAGES} ảnh.`,
      });
      return;
    }

    const accepted: File[] = [];
    let invalidTypeCount = 0;
    let tooLargeCount = 0;
    let overflowCount = 0;

    for (const file of selectedFiles) {
      if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
        invalidTypeCount++;
        continue;
      }
      if (file.size > MAX_IMAGE_BYTES) {
        tooLargeCount++;
        continue;
      }
      if (accepted.length >= remaining) {
        overflowCount++;
        continue;
      }
      accepted.push(file);
    }

    if (invalidTypeCount > 0) {
      this.messageService.add({
        severity: "error",
        summary: "Tệp không hợp lệ",
        detail: `${invalidTypeCount} tệp không phải ảnh hợp lệ (chỉ nhận JPEG, PNG, WEBP, GIF).`,
      });
    }
    if (tooLargeCount > 0) {
      this.messageService.add({
        severity: "error",
        summary: "Ảnh quá lớn",
        detail: `${tooLargeCount} ảnh vượt quá ${this.maxImageSizeMb}MB.`,
      });
    }
    if (overflowCount > 0) {
      this.messageService.add({
        severity: "warn",
        summary: "Vượt giới hạn ảnh",
        detail: `${overflowCount} ảnh chưa được thêm vì mỗi sản phẩm chỉ có tối đa ${MAX_IMAGES} ảnh.`,
      });
    }

    if (accepted.length === 0) {
      return;
    }

    this.uploadFiles(accepted);
  }

  private uploadFiles(files: File[]): void {
    this.uploading.set(true);
    let uploadedCount = 0;
    let failedCount = 0;

    from(files)
      .pipe(
        concatMap((file) =>
          this.productImageService.upload(file).pipe(
            tap({
              next: (image) => {
                uploadedCount++;
                this.appendImage(image);
              },
              error: () => {
                failedCount++;
              },
            }),
            catchError(() => EMPTY),
          ),
        ),
        finalize(() => this.uploading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        complete: () => {
          if (uploadedCount > 0) {
            this.messageService.add({
              severity: "success",
              summary: "Thành công",
              detail: `Đã tải lên ${uploadedCount} ảnh.`,
            });
          }
          if (failedCount > 0) {
            this.messageService.add({
              severity: "error",
              summary: "Lỗi",
              detail: `${failedCount} ảnh tải lên thất bại, vui lòng thử lại.`,
            });
          }
        },
        error: () => {
          this.uploading.set(false);
          this.messageService.add({
            severity: "error",
            summary: "Lỗi",
            detail: "Không tải được ảnh lên, vui lòng thử lại.",
          });
        },
      });
  }

  private appendImage(image: ProductImage): void {
    this.imagesChanged = true;
    this.images.update((list) => [...list, image]);
    if (this.primaryImageId() === null) {
      this.primaryImageId.set(image.id);
    }
  }

  isPrimary(image: ProductImage): boolean {
    return this.primaryImageId() === image.id;
  }

  setPrimary(imageId: number): void {
    this.imagesChanged = true;
    this.primaryImageId.set(imageId);
  }

  isBroken(imageId: number): boolean {
    return this.brokenImageIds().has(imageId);
  }

  onImageError(imageId: number): void {
    this.brokenImageIds.update((current) => {
      const next = new Set(current);
      next.add(imageId);
      return next;
    });
  }

  confirmRemove(image: ProductImage): void {
    const name = image.fileName ?? "này";
    this.confirmationService.confirm({
      message: `Xoá ảnh "${name}" khỏi sản phẩm?`,
      header: "Xác nhận xoá ảnh",
      icon: "pi pi-exclamation-triangle",
      acceptLabel: "Xoá ảnh",
      rejectLabel: "Huỷ",
      acceptButtonStyleClass: "p-button-danger",
      accept: () => this.removeImage(image),
    });
  }

  private removeImage(image: ProductImage): void {
    this.imagesChanged = true;
    const remaining = this.images().filter((item) => item.id !== image.id);
    this.images.set(remaining);
    if (this.primaryImageId() === image.id) {
      this.primaryImageId.set(remaining.length > 0 ? remaining[0].id : null);
    }
    this.brokenImageIds.update((current) => {
      if (!current.has(image.id)) {
        return current;
      }
      const next = new Set(current);
      next.delete(image.id);
      return next;
    });

    // Reset queue của p-fileUpload để tránh kẹt trạng thái pending upload
    this.imageUploader()?.clear();

    // Ảnh đã thuộc sản phẩm đã lưu: backend xoá khi lưu form.
    if (this.isPersistedImage(image.id)) {
      return;
    }

    // Ảnh mới upload trong phiên: dọn ngay trên máy chủ.
    this.productImageService
      .delete(image.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: () => {
          this.messageService.add({
            severity: "warn",
            summary: "Chưa dọn được ảnh",
            detail: "Ảnh đã bỏ khỏi danh sách nhưng chưa xoá được trên máy chủ.",
          });
        },
      });
  }

  private isPersistedImage(imageId: number): boolean {
    const saved = this.product()?.images ?? [];
    return saved.some((image) => image.id === imageId);
  }

  // --- Lưu ---------------------------------------------------------------

  onSubmit(): void {
    if (this.uploading()) {
      this.messageService.add({
        severity: "warn",
        summary: "Đang tải ảnh",
        detail: "Vui lòng đợi tải ảnh xong trước khi lưu.",
      });
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.skus.markAllAsTouched();
      this.skus.updateValueAndValidity();
      this.messageService.add({
        severity: "error",
        summary: "Chưa thể lưu",
        detail: "Vui lòng kiểm tra lại các trường được đánh dấu.",
      });
      return;
    }

    this.saving.set(true);

    const formValue = this.form.getRawValue();
    const rawSkus = this.skus.getRawValue() as SkuFormValue[];
    const skus: SkuRequest[] = rawSkus.map((sku) => ({
      id: sku.id ?? undefined,
      skuCode: (sku.skuCode ?? "").trim() || null,
      variant: sku.variant && sku.variant.trim() !== "" ? sku.variant.trim() : null,
      price: Number(sku.price ?? 0),
      stock: Number(sku.stock ?? 0),
    }));

    const imageIds = this.images().map((image) => image.id);
    const selectedPrimaryId = this.primaryImageId();
    const primaryImageId =
      selectedPrimaryId !== null && imageIds.includes(selectedPrimaryId)
        ? selectedPrimaryId
        : (imageIds[0] ?? null);

    const req: ProductRequest = {
      name: (formValue.name ?? "").trim(),
      description: formValue.description?.trim() || null,
      category: formValue.category?.trim() || null,
      platformSource: formValue.platformSource || "MANUAL",
      imageIds,
      primaryImageId,
      skus,
    };

    const editingId = this.isEdit() ? this.productId() : null;
    const save$ = editingId
      ? this.productService.updateProduct(editingId, req)
      : this.productService.createProduct(req);

    save$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (product) => {
        this.saved = true;
        this.messageService.add({
          severity: "success",
          summary: "Thành công",
          detail: editingId ? "Đã cập nhật sản phẩm." : "Đã tạo sản phẩm mới.",
        });
        this.router.navigate(["/products", product.id]);
      },
      error: (err) => {
        this.saving.set(false);
        const detail =
          err?.error?.message ?? (editingId ? "Cập nhật sản phẩm thất bại." : "Tạo sản phẩm thất bại.");
        this.messageService.add({ severity: "error", summary: "Lỗi", detail });
      },
    });
  }

  getSkuGroup(index: number): FormGroup {
    return this.skus.at(index) as FormGroup;
  }

  formatPrice(value: number | null): string {
    return value === null || value === undefined ? "—" : formatVnd(value);
  }
}

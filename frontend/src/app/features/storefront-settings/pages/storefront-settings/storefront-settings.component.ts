import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed, toSignal } from "@angular/core/rxjs-interop";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { catchError, finalize, of } from "rxjs";
import { ConfirmationService, MessageService } from "primeng/api";
import { ButtonModule } from "primeng/button";
import { CardModule } from "primeng/card";
import { ConfirmDialogModule } from "primeng/confirmdialog";
import { InputSwitchModule } from "primeng/inputswitch";
import { InputTextModule } from "primeng/inputtext";
import { AuthService } from "@core/auth/auth.service";
import { StorefrontSettings } from "@features/storefront-settings/models/storefront-settings.model";
import { StorefrontSettingsService } from "@features/storefront-settings/services/storefront-settings.service";

// Khớp với ràng buộc của UpdateStorefrontRequest ở backend.
const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const SLUG_MIN_LENGTH = 3;
const SLUG_MAX_LENGTH = 100;
const PHONE_PATTERN = /^\+?[0-9]{8,15}$/;

@Component({
  selector: "app-storefront-settings",
  standalone: true,
  imports: [
    ReactiveFormsModule,
    ButtonModule,
    CardModule,
    ConfirmDialogModule,
    InputSwitchModule,
    InputTextModule,
  ],
  providers: [ConfirmationService],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: "./storefront-settings.component.html",
  styleUrls: ["./storefront-settings.component.scss"],
})
export class StorefrontSettingsComponent {
  private readonly fb = inject(FormBuilder);
  private readonly settingsService = inject(StorefrontSettingsService);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly isOwner = inject(AuthService).isOwner;
  readonly origin = window.location.origin;

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly loadError = signal<string | null>(null);
  readonly saved = signal<StorefrontSettings | null>(null);

  readonly form = this.fb.nonNullable.group({
    storefrontEnabled: [false],
    slug: [
      "",
      [
        Validators.required,
        Validators.minLength(SLUG_MIN_LENGTH),
        Validators.maxLength(SLUG_MAX_LENGTH),
        Validators.pattern(SLUG_PATTERN),
      ],
    ],
    contactPhone: ["", [Validators.pattern(PHONE_PATTERN)]],
  });

  private readonly slugValue = toSignal(this.form.controls.slug.valueChanges, {
    initialValue: "",
  });

  /** Slug đang lưu có thể sai định dạng, như `-1` từ bộ sinh slug cũ: link đó không mở được. */
  readonly savedSlugValid = computed(() => {
    const slug = this.saved()?.slug ?? "";
    return slug.length >= SLUG_MIN_LENGTH && slug.length <= SLUG_MAX_LENGTH && SLUG_PATTERN.test(slug);
  });

  readonly storefrontUrl = computed(() => `${this.origin}/s/${this.saved()?.slug ?? ""}`);

  readonly slugChanged = computed(() => {
    const saved = this.saved();
    return !!saved && this.savedSlugValid() && this.slugValue().trim() !== saved.slug;
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.settingsService
      .getSettings()
      .pipe(
        catchError((err) => {
          this.loadError.set(err?.error?.message ?? "Không tải được cài đặt trang bán hàng.");
          return of(null);
        }),
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((settings) => {
        if (settings) {
          this.applySettings(settings);
        }
      });
  }

  onSubmit(): void {
    const { slug, contactPhone } = this.form.getRawValue();
    this.form.controls.slug.setValue(slug.trim());
    this.form.controls.contactPhone.setValue(contactPhone.trim());

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const warning = this.confirmMessage();
    if (!warning) {
      this.save();
      return;
    }
    this.confirmationService.confirm({
      header: "Xác nhận lưu thay đổi",
      message: warning,
      icon: "pi pi-exclamation-triangle",
      acceptLabel: "Lưu thay đổi",
      rejectLabel: "Huỷ",
      accept: () => this.save(),
    });
  }

  private confirmMessage(): string | null {
    const turningOff =
      !!this.saved()?.storefrontEnabled && !this.form.controls.storefrontEnabled.value;
    if (turningOff && this.slugChanged()) {
      return "Trang bán hàng sẽ tắt và địa chỉ sẽ đổi. Link đã gửi cho khách sẽ không mở được nữa.";
    }
    if (turningOff) {
      return "Trang bán hàng sẽ tắt. Khách mở link sẽ thấy trang không tìm thấy.";
    }
    if (this.slugChanged()) {
      return "Địa chỉ sẽ đổi. Link đã gửi cho khách sẽ không mở được nữa, hệ thống không tự chuyển sang địa chỉ mới.";
    }
    return null;
  }

  private save(): void {
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.settingsService
      .updateSettings({
        storefrontEnabled: value.storefrontEnabled,
        slug: value.slug,
        contactPhone: value.contactPhone || null,
      })
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (settings) => {
          this.applySettings(settings);
          this.messageService.add({
            severity: "success",
            summary: "Đã lưu",
            detail: "Đã lưu cài đặt trang bán hàng",
          });
        },
        error: (err) => {
          this.messageService.add({
            severity: "error",
            summary: "Không lưu được",
            detail: err?.error?.message ?? "Không lưu được cài đặt trang bán hàng, vui lòng thử lại.",
          });
        },
      });
  }

  private applySettings(settings: StorefrontSettings): void {
    this.saved.set(settings);
    this.form.setValue({
      storefrontEnabled: settings.storefrontEnabled,
      slug: settings.slug,
      contactPhone: settings.contactPhone ?? "",
    });
    this.form.markAsPristine();
    // Slug xấu sẵn có phải báo ngay, không đợi chủ shop bấm vào ô.
    this.form.controls.slug.markAsTouched();
    if (this.isOwner()) {
      this.form.enable();
    } else {
      this.form.disable();
    }
  }
}

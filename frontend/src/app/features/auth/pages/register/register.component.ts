import { Component, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { ReactiveFormsModule, FormBuilder, Validators } from "@angular/forms";
import { Router, RouterLink } from "@angular/router";
import { ButtonModule } from "primeng/button";
import { InputTextModule } from "primeng/inputtext";
import { PasswordModule } from "primeng/password";
import { MessageModule } from "primeng/message";
import { DropdownModule } from "primeng/dropdown";
import { CheckboxModule } from "primeng/checkbox";
import { IconFieldModule } from "primeng/iconfield";
import { InputIconModule } from "primeng/inputicon";
import { AuthService } from "@core/auth/auth.service";
import { RegisterRequest } from "@core/models/auth.model";
import {
  REGISTER_DEFAULT_PLATFORM,
  REGISTER_PLATFORM_OPTIONS,
} from "@features/auth/constants/register.constants";

@Component({
  selector: "app-register",
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    InputTextModule,
    PasswordModule,
    MessageModule,
    DropdownModule,
    CheckboxModule,
    IconFieldModule,
    InputIconModule,
  ],
  templateUrl: "./register.component.html",
})
export class RegisterComponent {
  platforms = REGISTER_PLATFORM_OPTIONS;

  form = this.fb.group({
    shopName: ["", [Validators.required, Validators.minLength(2)]],
    platform: [REGISTER_DEFAULT_PLATFORM, Validators.required],
    fullName: ["", Validators.required],
    email: ["", [Validators.required, Validators.email]],
    password: ["", [Validators.required, Validators.minLength(6)]],
    acceptTerms: [false, Validators.requiredTrue],
  });

  loading = signal(false);
  errorMsg = signal<string | null>(null);

  constructor(
    private fb: FormBuilder,
    private auth: AuthService,
    private router: Router,
  ) {}

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.errorMsg.set(null);

    const v = this.form.value;
    const body: RegisterRequest = {
      shopName: v.shopName!,
      platform: v.platform!,
      fullName: v.fullName!,
      email: v.email!,
      password: v.password!,
    };

    this.auth.register(body).subscribe({
      next: () => this.router.navigate(["/dashboard"]),
      error: (err) => {
        this.errorMsg.set(err.error?.message ?? "Đăng ký thất bại");
        this.loading.set(false);
      },
    });
  }
}

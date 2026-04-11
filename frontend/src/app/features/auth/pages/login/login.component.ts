import { Component, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { ReactiveFormsModule, FormBuilder, Validators } from "@angular/forms";
import { Router, RouterLink } from "@angular/router";
import { ButtonModule } from "primeng/button";
import { InputTextModule } from "primeng/inputtext";
import { PasswordModule } from "primeng/password";
import { MessageModule } from "primeng/message";
import { CheckboxModule } from "primeng/checkbox";
import { IconFieldModule } from "primeng/iconfield";
import { InputIconModule } from "primeng/inputicon";
import { AuthService } from "@core/auth/auth.service";

@Component({
  selector: "app-login",
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    InputTextModule,
    PasswordModule,
    MessageModule,
    CheckboxModule,
    IconFieldModule,
    InputIconModule,
  ],
  templateUrl: "./login.component.html",
})
export class LoginComponent {
  form = this.fb.group({
    email: ["", [Validators.required, Validators.email]],
    password: ["", Validators.required],
    rememberMe: [false],
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

    this.auth
      .login(this.form.value as { email: string; password: string })
      .subscribe({
        next: () => this.router.navigate(["/dashboard"]),
        error: (err) => {
          this.errorMsg.set(err.error?.message ?? "Đăng nhập thất bại");
          this.loading.set(false);
        },
      });
  }
}

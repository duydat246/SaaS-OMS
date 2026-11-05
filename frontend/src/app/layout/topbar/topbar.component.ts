import { Component, computed, inject, OnDestroy, OnInit, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { NavigationEnd, Router } from "@angular/router";
import { ButtonModule } from "primeng/button";
import { TooltipModule } from "primeng/tooltip";
import { AuthService } from "@core/auth/auth.service";
import { ShellLayoutService } from "../shell-layout.service";
import { filter, Subscription } from "rxjs";

const ROLE_LABEL_VI: Record<string, string> = {
  OWNER: "Chủ cửa hàng",
  STAFF: "Nhân viên",
};

@Component({
  selector: "app-topbar",
  standalone: true,
  imports: [CommonModule, ButtonModule, TooltipModule],
  template: `
    <header class="topbar">
      <div class="topbar-left">
        <p-button
          class="menu-toggle"
          [icon]="menuToggleIcon()"
          [rounded]="true"
          [text]="true"
          severity="secondary"
          type="button"
          [attr.aria-expanded]="menuToggleAriaExpanded()"
          aria-controls="app-sidebar-nav"
          [pTooltip]="menuToggleTooltip()"
          [ariaLabel]="menuToggleTooltip()"
          tooltipPosition="bottom"
          (click)="layout.toggleSidebar()"
        />
        <h1 class="page-title">{{ pageTitle() }}</h1>
      </div>
      <div class="topbar-right">
        <span class="user-name">{{ auth.currentUser()?.fullName }}</span>
        @if (roleLabel()) {
          <span class="user-role">{{ roleLabel() }}</span>
        }
        <p-button
          icon="pi pi-sign-out"
          [rounded]="true"
          [text]="true"
          severity="secondary"
          (click)="auth.logout()"
          pTooltip="Đăng xuất"
          ariaLabel="Đăng xuất"
          tooltipPosition="bottom"
          type="button"
        />
      </div>
    </header>
  `,
})
export class TopbarComponent implements OnInit, OnDestroy {
  pageTitle = signal("Tổng quan");
  readonly layout = inject(ShellLayoutService);
  readonly roleLabel = computed(() => {
    const role = this.auth.currentUser()?.role;
    return role ? (ROLE_LABEL_VI[role] ?? role) : "";
  });
  private sub?: Subscription;

  constructor(
    public auth: AuthService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.setTitleFromUrl(this.router.url);
    this.sub = this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => this.setTitleFromUrl(e.urlAfterRedirects));
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  menuToggleIcon(): string {
    if (this.layout.isMobileViewport()) {
      return this.layout.mobileNavOpen() ? "pi pi-times" : "pi pi-bars";
    }
    return this.layout.sidebarCollapsed()
      ? "pi pi-angle-double-right"
      : "pi pi-bars";
  }

  menuToggleAriaExpanded(): boolean {
    return this.layout.isMobileViewport()
      ? this.layout.mobileNavOpen()
      : !this.layout.sidebarCollapsed();
  }

  menuToggleTooltip(): string {
    if (this.layout.isMobileViewport()) {
      return this.layout.mobileNavOpen() ? "Đóng menu" : "Mở menu";
    }
    return this.layout.sidebarCollapsed() ? "Mở rộng menu" : "Thu gọn menu";
  }

  private setTitleFromUrl(url: string): void {
    if (url.includes("/orders/new")) {
      this.pageTitle.set("Tạo đơn hàng");
    } else if (url.includes("/orders/") && /\/orders\/\d+/.test(url)) {
      this.pageTitle.set("Chi tiết đơn hàng");
    } else if (url.includes("/orders")) {
      this.pageTitle.set("Đơn hàng");
    } else if (url.includes("/products/new")) {
      this.pageTitle.set("Tạo sản phẩm");
    } else if (
      url.includes("/products/") &&
      /\/products\/\d+\/edit/.test(url)
    ) {
      this.pageTitle.set("Sửa sản phẩm");
    } else if (url.includes("/products/") && /\/products\/\d+/.test(url)) {
      this.pageTitle.set("Chi tiết sản phẩm");
    } else if (url.includes("/products")) {
      this.pageTitle.set("Sản phẩm");
    } else if (url.includes("/storefront-settings")) {
      this.pageTitle.set("Trang bán hàng");
    } else if (url.includes("/dashboard")) {
      this.pageTitle.set("Tổng quan");
    } else {
      this.pageTitle.set("SaaS OMS");
    }
  }
}

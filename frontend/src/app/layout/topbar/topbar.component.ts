import { Component, inject, OnDestroy, OnInit, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { NavigationEnd, Router } from "@angular/router";
import { ButtonModule } from "primeng/button";
import { InputTextModule } from "primeng/inputtext";
import { IconFieldModule } from "primeng/iconfield";
import { InputIconModule } from "primeng/inputicon";
import { TooltipModule } from "primeng/tooltip";
import { AuthService } from "@core/auth/auth.service";
import { ShellLayoutService } from "../shell-layout.service";
import { filter, Subscription } from "rxjs";

@Component({
  selector: "app-topbar",
  standalone: true,
  imports: [
    CommonModule,
    ButtonModule,
    InputTextModule,
    IconFieldModule,
    InputIconModule,
    TooltipModule,
  ],
  template: `
    <header class="topbar">
      <div class="topbar-left">
        <p-button
          class="menu-toggle"
          [icon]="
            layout.sidebarCollapsed()
              ? 'pi pi-angle-double-right'
              : 'pi pi-bars'
          "
          [rounded]="true"
          [text]="true"
          severity="secondary"
          type="button"
          [attr.aria-expanded]="!layout.sidebarCollapsed()"
          aria-controls="app-sidebar-nav"
          [pTooltip]="
            layout.sidebarCollapsed() ? 'Mở rộng menu' : 'Thu gọn menu'
          "
          tooltipPosition="bottom"
          (click)="layout.toggleSidebar()"
        />
        <h1 class="page-title">{{ pageTitle() }}</h1>
      </div>
      <div class="topbar-right">
        <p-iconField iconPosition="left" styleClass="topbar-search">
          <p-inputIcon styleClass="pi pi-search" />
          <input
            pInputText
            type="search"
            placeholder="Tìm kiếm..."
            autocomplete="off"
          />
        </p-iconField>
        <p-button
          icon="pi pi-globe"
          [rounded]="true"
          [text]="true"
          severity="secondary"
          type="button"
        />
        <span class="user-name">{{ auth.currentUser()?.fullName }}</span>
        <span class="user-role">{{ auth.currentUser()?.role }}</span>
        <p-button
          icon="pi pi-sign-out"
          [rounded]="true"
          [text]="true"
          severity="secondary"
          (click)="auth.logout()"
          pTooltip="Đăng xuất"
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

  private setTitleFromUrl(url: string): void {
    if (url.includes("/orders/new")) {
      this.pageTitle.set("Tạo đơn hàng");
    } else if (url.includes("/orders/") && /\/orders\/\d+/.test(url)) {
      this.pageTitle.set("Chi tiết đơn hàng");
    } else if (url.includes("/orders")) {
      this.pageTitle.set("Đơn hàng");
    } else if (url.includes("/dashboard")) {
      this.pageTitle.set("Tổng quan");
    } else {
      this.pageTitle.set("SaaS OMS");
    }
  }
}

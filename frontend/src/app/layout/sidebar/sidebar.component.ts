import { Component, inject } from "@angular/core";
import { RouterLink, RouterLinkActive } from "@angular/router";
import { CommonModule } from "@angular/common";
import { TooltipModule } from "primeng/tooltip";
import { AuthService } from "@core/auth/auth.service";
import { ShellLayoutService } from "../shell-layout.service";

interface NavItem {
  label: string;
  icon: string;
  route: string;
}

@Component({
  selector: "app-sidebar",
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, TooltipModule],
  template: `
    <nav id="app-sidebar-nav" class="sidebar" aria-label="Menu chính">
      <div class="sidebar-logo">
        <span class="logo-mark" aria-hidden="true">
          <i class="pi pi-shopping-bag"></i>
        </span>
        <span class="logo-text">SaaS OMS</span>
      </div>

      <div class="sidebar-shop">{{ auth.currentUser()?.shopName }}</div>

      <div class="nav-section-label">MENU</div>
      <ul class="nav-list">
        @for (item of navItems; track item.route) {
          <li>
            <a
              [routerLink]="item.route"
              routerLinkActive="active"
              class="nav-item"
              [pTooltip]="item.label"
              [tooltipDisabled]="!layout.sidebarCollapsed()"
              tooltipPosition="right"
              [showDelay]="200"
              (click)="layout.closeMobileNav()"
            >
              <i [class]="'pi ' + item.icon" aria-hidden="true"></i>
              <span>{{ item.label }}</span>
            </a>
          </li>
        }
      </ul>
    </nav>
  `,
})
export class SidebarComponent {
  readonly layout = inject(ShellLayoutService);

  navItems: NavItem[] = [
    { label: "Tổng quan", icon: "pi-home", route: "/dashboard" },
    { label: "Đơn hàng", icon: "pi-shopping-cart", route: "/orders" },
    { label: "Sản phẩm", icon: "pi-box", route: "/products" },
  ];

  constructor(public auth: AuthService) {}
}

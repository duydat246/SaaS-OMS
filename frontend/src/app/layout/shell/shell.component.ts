import { Component, HostListener, inject, OnInit } from "@angular/core";
import { RouterOutlet } from "@angular/router";
import { ToastModule } from "primeng/toast";
import { SidebarComponent } from "../sidebar/sidebar.component";
import { TopbarComponent } from "../topbar/topbar.component";
import {
  ShellLayoutService,
  SHELL_MOBILE_MAX_WIDTH_PX,
} from "../shell-layout.service";

@Component({
  selector: "app-shell",
  standalone: true,
  imports: [RouterOutlet, ToastModule, SidebarComponent, TopbarComponent],
  template: `
    <p-toast position="bottom-left" styleClass="apollo-toast" />
    <div
      class="layout-wrapper apollo-layout"
      [class.apollo-layout--sidebar-collapsed]="
        !layout.isMobileViewport() && layout.sidebarCollapsed()
      "
      [class.apollo-layout--mobile-nav-open]="
        layout.isMobileViewport() && layout.mobileNavOpen()
      "
    >
      @if (layout.isMobileViewport() && layout.mobileNavOpen()) {
        <div
          class="apollo-mobile-backdrop"
          role="presentation"
          (click)="layout.closeMobileNav()"
        ></div>
      }
      <app-sidebar />
      <div class="layout-main">
        <app-topbar />
        <div class="layout-content">
          <router-outlet />
        </div>
      </div>
    </div>
  `,
})
export class ShellComponent implements OnInit {
  readonly layout = inject(ShellLayoutService);

  private readonly mobileMql = matchMedia(
    `(max-width: ${SHELL_MOBILE_MAX_WIDTH_PX}px)`,
  );

  constructor() {
    this.layout.setMobileViewport(this.mobileMql.matches);
  }

  @HostListener("document:keydown.escape")
  onEscape(): void {
    if (this.layout.isMobileViewport() && this.layout.mobileNavOpen()) {
      this.layout.closeMobileNav();
    }
  }

  ngOnInit(): void {
    this.syncViewport();
    if (this.mobileMql.addEventListener) {
      this.mobileMql.addEventListener("change", () => this.syncViewport());
    } else {
      this.mobileMql.addListener(() => this.syncViewport());
    }
  }

  private syncViewport(): void {
    this.layout.setMobileViewport(this.mobileMql.matches);
  }
}

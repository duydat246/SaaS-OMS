import { Injectable, signal } from "@angular/core";

/** Màn hình ≤ breakpoint coi là mobile (drawer menu thay vì thu gọn rail). */
export const SHELL_MOBILE_MAX_WIDTH_PX = 767.98;

@Injectable({ providedIn: "root" })
export class ShellLayoutService {
  readonly sidebarCollapsed = signal(false);
  /** true khi viewport ≤ SHELL_MOBILE_MAX_WIDTH_PX */
  readonly isMobileViewport = signal(false);
  /** Drawer sidebar mở (chỉ dùng khi mobile). */
  readonly mobileNavOpen = signal(false);

  setMobileViewport(isMobile: boolean): void {
    const wasMobile = this.isMobileViewport();
    this.isMobileViewport.set(isMobile);
    if (wasMobile && !isMobile) {
      this.mobileNavOpen.set(false);
    }
  }

  closeMobileNav(): void {
    this.mobileNavOpen.set(false);
  }

  toggleSidebar(): void {
    if (this.isMobileViewport()) {
      this.mobileNavOpen.update((open) => !open);
      if (this.mobileNavOpen()) {
        this.sidebarCollapsed.set(false);
      }
      return;
    }
    this.sidebarCollapsed.update((c) => !c);
  }
}

import { Injectable, signal } from "@angular/core";

@Injectable({ providedIn: "root" })
export class ShellLayoutService {
  readonly sidebarCollapsed = signal(false);

  toggleSidebar(): void {
    this.sidebarCollapsed.update((c) => !c);
  }
}

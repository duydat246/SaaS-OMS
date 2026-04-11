import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastModule } from 'primeng/toast';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { TopbarComponent } from '../topbar/topbar.component';
import { ShellLayoutService } from '../shell-layout.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, ToastModule, SidebarComponent, TopbarComponent],
  template: `
    <p-toast position="bottom-left" styleClass="apollo-toast" />
    <div
      class="layout-wrapper apollo-layout"
      [class.apollo-layout--sidebar-collapsed]="layout.sidebarCollapsed()"
    >
      <app-sidebar />
      <div class="layout-main">
        <app-topbar />
        <div class="layout-content">
          <router-outlet />
        </div>
      </div>
    </div>
  `
})
export class ShellComponent {
  readonly layout = inject(ShellLayoutService);
}

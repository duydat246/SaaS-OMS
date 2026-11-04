import { Component, inject } from "@angular/core";
import { RouterOutlet } from "@angular/router";
import { PrimeNGConfig } from "primeng/api";
import { PRIMENG_VI, PRIMENG_VI_ARIA } from "@core/i18n/primeng-vi";

@Component({
  selector: "app-root",
  standalone: true,
  imports: [RouterOutlet],
  template: "<router-outlet />",
})
export class AppComponent {
  constructor() {
    const primeng = inject(PrimeNGConfig);
    primeng.setTranslation({
      ...PRIMENG_VI,
      aria: { ...primeng.translation.aria, ...PRIMENG_VI_ARIA },
    });
  }
}

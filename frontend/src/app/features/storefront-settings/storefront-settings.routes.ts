import { Routes } from "@angular/router";

export const storefrontSettingsRoutes: Routes = [
  {
    path: "",
    loadComponent: () =>
      import("./pages/storefront-settings/storefront-settings.component").then(
        (m) => m.StorefrontSettingsComponent,
      ),
  },
];

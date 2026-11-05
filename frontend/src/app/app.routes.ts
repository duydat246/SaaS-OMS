import { Routes } from "@angular/router";
import { authGuard } from "./core/auth/auth.guard";

export const routes: Routes = [
  {
    path: "auth",
    loadChildren: () =>
      import("./features/auth/auth.routes").then((m) => m.authRoutes),
  },
  // Trang khách công khai: phải đứng trước route "" có authGuard và không có guard.
  {
    path: "s",
    loadChildren: () =>
      import("./features/storefront/storefront.routes").then(
        (m) => m.storefrontRoutes,
      ),
  },
  {
    path: "",
    loadComponent: () =>
      import("./layout/shell/shell.component").then((m) => m.ShellComponent),
    canActivate: [authGuard],
    children: [
      {
        path: "dashboard",
        loadChildren: () =>
          import("./features/dashboard/dashboard.routes").then(
            (m) => m.dashboardRoutes,
          ),
      },
      {
        path: "orders",
        loadChildren: () =>
          import("./features/orders/orders.routes").then((m) => m.ordersRoutes),
      },
      {
        path: "products",
        loadChildren: () =>
          import("./features/products/products.routes").then(
            (m) => m.productsRoutes,
          ),
      },
      {
        path: "storefront-settings",
        loadChildren: () =>
          import("./features/storefront-settings/storefront-settings.routes").then(
            (m) => m.storefrontSettingsRoutes,
          ),
      },
      { path: "", redirectTo: "dashboard", pathMatch: "full" },
    ],
  },
  { path: "**", redirectTo: "/dashboard" },
];

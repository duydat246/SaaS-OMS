import { Routes } from "@angular/router";

const loadShell = () =>
  import("../../layout/storefront-shell/storefront-shell.component").then(
    (m) => m.StorefrontShellComponent,
  );

export const storefrontRoutes: Routes = [
  // `/s` không có slug: layout tự hiện trang không tìm thấy, không rơi vào route `**` về dashboard.
  { path: "", pathMatch: "full", loadComponent: loadShell },
  {
    path: ":slug",
    loadComponent: loadShell,
    children: [
      {
        path: "",
        pathMatch: "full",
        loadComponent: () =>
          import("./pages/storefront-home/storefront-home.component").then(
            (m) => m.StorefrontHomeComponent,
          ),
      },
      {
        path: "p/:id",
        loadComponent: () =>
          import("./pages/storefront-product/storefront-product.component").then(
            (m) => m.StorefrontProductComponent,
          ),
      },
      {
        path: "**",
        // Input binding lấy cả query param: data thắng query, nên `?heading=` trên link không đổi được chữ trên trang.
        data: {
          heading: "Không tìm thấy trang",
          detail: "Đường link này không đúng hoặc đã thay đổi.",
        },
        loadComponent: () =>
          import("./pages/storefront-not-found/storefront-not-found.component").then(
            (m) => m.StorefrontNotFoundComponent,
          ),
      },
    ],
  },
];

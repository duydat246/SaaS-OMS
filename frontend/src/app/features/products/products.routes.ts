import { Routes } from "@angular/router";

export const productsRoutes: Routes = [
  {
    path: "",
    loadComponent: () =>
      import("./pages/product-list/product-list.component").then(
        (m) => m.ProductListComponent,
      ),
  },
  {
    path: "new",
    loadComponent: () =>
      import("./pages/product-form/product-form.component").then(
        (m) => m.ProductFormComponent,
      ),
  },
  {
    path: ":id/edit",
    loadComponent: () =>
      import("./pages/product-form/product-form.component").then(
        (m) => m.ProductFormComponent,
      ),
  },
  {
    path: ":id",
    loadComponent: () =>
      import("./pages/product-detail/product-detail.component").then(
        (m) => m.ProductDetailComponent,
      ),
  },
];

import { CanDeactivateFn, Routes } from "@angular/router";
import type { ProductFormComponent } from "./pages/product-form/product-form.component";

const confirmLeaveForm: CanDeactivateFn<ProductFormComponent> = (form) => form.canDeactivate();

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
    canDeactivate: [confirmLeaveForm],
  },
  {
    path: ":id/edit",
    loadComponent: () =>
      import("./pages/product-form/product-form.component").then(
        (m) => m.ProductFormComponent,
      ),
    canDeactivate: [confirmLeaveForm],
  },
  {
    path: ":id",
    loadComponent: () =>
      import("./pages/product-detail/product-detail.component").then(
        (m) => m.ProductDetailComponent,
      ),
  },
];

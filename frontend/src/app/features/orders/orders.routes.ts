import { Routes } from "@angular/router";

export const ordersRoutes: Routes = [
  {
    path: "",
    loadComponent: () =>
      import("./pages/order-list/order-list.component").then(
        (m) => m.OrderListComponent,
      ),
  },
  {
    path: "new",
    loadComponent: () =>
      import("./pages/order-form/order-form.component").then(
        (m) => m.OrderFormComponent,
      ),
  },
  {
    path: ":id",
    loadComponent: () =>
      import("./pages/order-detail/order-detail.component").then(
        (m) => m.OrderDetailComponent,
      ),
  },
];

import { Component, OnInit, inject, signal, Input } from "@angular/core";
import { CommonModule } from "@angular/common";
import { Router, RouterLink } from "@angular/router";
import { MessageService } from "primeng/api";
import { CardModule } from "primeng/card";
import { ButtonModule } from "primeng/button";
import { DialogModule } from "primeng/dialog";
import { TagModule } from "primeng/tag";
import { TableModule } from "primeng/table";
import { DropdownModule } from "primeng/dropdown";
import { Order, OrderStatus } from "@features/orders/models/order.model";
import { orderStatusLabelVi } from "@features/orders/models/order-status";
import { OrderService } from "@features/orders/services/order.service";
import {
  ORDER_STATUS_SEVERITY,
  ORDER_STATUS_TRANSITIONS,
} from "@features/orders/constants/order.constants";

type TagSeverity =
  | "success"
  | "info"
  | "secondary"
  | "contrast"
  | "warning"
  | "danger";

@Component({
  selector: "app-order-detail",
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    CardModule,
    ButtonModule,
    DialogModule,
    TagModule,
    TableModule,
    DropdownModule,
  ],
  templateUrl: "./order-detail.component.html",
  styleUrls: ["./order-detail.component.scss"],
})
export class OrderDetailComponent implements OnInit {
  @Input() id!: string;

  order = signal<Order | null>(null);
  loading = signal(true);
  updating = signal(false);
  cancelDialogVisible = signal(false);

  nextStatuses = signal<OrderStatus[]>([]);

  private readonly orderService = inject(OrderService);
  private readonly router = inject(Router);
  private readonly messageService = inject(MessageService);

  ngOnInit(): void {
    this.orderService.getOrder(Number(this.id)).subscribe({
      next: (order) => {
        this.order.set(order);
        this.nextStatuses.set(ORDER_STATUS_TRANSITIONS[order.status]);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.router.navigate(["/orders"]);
      },
    });
  }

  changeStatus(status: OrderStatus): void {
    this.updating.set(true);
    this.orderService.updateStatus(Number(this.id), status).subscribe({
      next: (updated) => {
        this.order.set(updated);
        this.nextStatuses.set(ORDER_STATUS_TRANSITIONS[updated.status]);
        this.updating.set(false);
        this.messageService.add({
          severity: "success",
          summary: "Thành công",
          detail: `Đã chuyển trạng thái sang "${this.orderStatusLabelVi(updated.status)}".`,
        });
      },
      error: (err) => {
        this.updating.set(false);
        const detail = err.error?.message ?? "Không thể cập nhật trạng thái.";
        this.messageService.add({ severity: "error", summary: "Lỗi", detail });
      },
    });
  }

  onSelectNextStatus(status: OrderStatus): void {
    if (status === "CANCELLED") {
      this.cancelDialogVisible.set(true);
      return;
    }

    this.changeStatus(status);
  }

  confirmCancelOrder(): void {
    this.cancelDialogVisible.set(false);
    this.changeStatus("CANCELLED");
  }

  closeCancelDialog(): void {
    this.cancelDialogVisible.set(false);
  }

  getSeverity(status: OrderStatus): TagSeverity {
    return ORDER_STATUS_SEVERITY[status] ?? "secondary";
  }

  orderStatusLabelVi = orderStatusLabelVi;
}

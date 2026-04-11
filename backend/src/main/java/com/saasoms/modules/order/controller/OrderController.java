package com.saasoms.modules.order.controller;

import com.saasoms.common.response.ApiResponse;
import com.saasoms.common.response.PageResponse;
import com.saasoms.modules.order.dto.CreateOrderRequest;
import com.saasoms.modules.order.dto.OrderResponse;
import com.saasoms.modules.order.dto.OrderStatsResponse;
import com.saasoms.modules.order.dto.UpdateStatusRequest;
import com.saasoms.modules.order.entity.OrderStatus;
import com.saasoms.modules.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders")
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "List orders with optional filters")
    public ApiResponse<PageResponse<OrderResponse>> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ApiResponse.ok(orderService.getOrders(status, search, pageable));
    }

    @GetMapping("/stats")
    @Operation(summary = "Dashboard order statistics for current shop")
    public ApiResponse<OrderStatsResponse> stats() {
        return ApiResponse.ok(orderService.getOrderStats());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order detail")
    public ApiResponse<OrderResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(orderService.getOrder(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a manual order")
    public ApiResponse<OrderResponse> create(@Valid @RequestBody CreateOrderRequest req) {
        return ApiResponse.ok(orderService.createOrder(req), "Order created");
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update order status")
    public ApiResponse<OrderResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest req) {
        return ApiResponse.ok(orderService.updateStatus(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Cancel/delete an order (OWNER only)")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ApiResponse.ok(null, "Order cancelled");
    }
}

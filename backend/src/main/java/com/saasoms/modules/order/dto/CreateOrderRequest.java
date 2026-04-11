package com.saasoms.modules.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateOrderRequest {

    @NotBlank(message = "Customer name is required")
    private String customerName;

    @NotBlank(message = "Customer phone is required")
    private String customerPhone;

    private String customerAddress;

    @PositiveOrZero
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @PositiveOrZero
    private BigDecimal discount = BigDecimal.ZERO;

    private String notes;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<OrderItemRequest> items;

    @Data
    public static class OrderItemRequest {
        @NotBlank(message = "Product name is required")
        private String productName;
        private String variant;
        private int quantity = 1;
        @PositiveOrZero
        private BigDecimal unitPrice = BigDecimal.ZERO;
    }
}

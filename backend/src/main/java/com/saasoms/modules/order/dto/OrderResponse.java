package com.saasoms.modules.order.dto;

import com.saasoms.modules.order.entity.Order;
import com.saasoms.modules.order.entity.OrderItem;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderResponse {
    private Long id;
    private String platformSource;
    private String platformOrderId;
    private String status;
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal discount;
    private BigDecimal totalAmount;
    private String shippingAddress;
    private String notes;
    private LocalDateTime orderedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private CustomerInfo customer;
    private List<ItemInfo> items;

    @Data
    @Builder
    public static class CustomerInfo {
        private Long id;
        private String fullName;
        private String phone;
        private String email;
        private String address;
    }

    @Data
    @Builder
    public static class ItemInfo {
        private Long id;
        private String skuCode;
        private String productName;
        private String variant;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
    }

    public static OrderResponse from(Order o) {
        return OrderResponse.builder()
                .id(o.getId())
                .platformSource(o.getPlatformSource())
                .platformOrderId(o.getPlatformOrderId())
                .status(o.getStatus().name())
                .subtotal(o.getSubtotal())
                .shippingFee(o.getShippingFee())
                .discount(o.getDiscount())
                .totalAmount(o.getTotalAmount())
                .shippingAddress(o.getShippingAddress())
                .notes(o.getNotes())
                .orderedAt(o.getOrderedAt())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .customer(o.getCustomer() == null ? null : CustomerInfo.builder()
                        .id(o.getCustomer().getId())
                        .fullName(o.getCustomer().getFullName())
                        .phone(o.getCustomer().getPhone())
                        .email(o.getCustomer().getEmail())
                        .address(o.getCustomer().getAddress())
                        .build())
                .items(o.getItems().stream().map(i -> ItemInfo.builder()
                        .id(i.getId())
                        .skuCode(i.getSkuCode())
                        .productName(i.getProductName())
                        .variant(i.getVariant())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .totalPrice(i.getTotalPrice())
                        .build()).toList())
                .build();
    }
}

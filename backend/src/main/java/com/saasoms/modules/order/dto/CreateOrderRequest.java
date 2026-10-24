package com.saasoms.modules.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateOrderRequest {

    @NotBlank(message = "Tên khách hàng không được để trống")
    @Size(max = 150, message = "Tên khách hàng tối đa 150 ký tự")
    private String customerName;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Size(max = 30, message = "Số điện thoại tối đa 30 ký tự")
    private String customerPhone;

    @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
    private String customerAddress;

    @DecimalMin(value = "0", message = "Phí vận chuyển không được âm")
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @DecimalMin(value = "0", message = "Giảm giá không được âm")
    private BigDecimal discount = BigDecimal.ZERO;

    private String notes;

    @NotEmpty(message = "Đơn hàng phải có ít nhất 1 sản phẩm")
    @Valid
    private List<OrderItemRequest> items;

    @Data
    public static class OrderItemRequest {
        @NotNull(message = "Vui lòng chọn sản phẩm")
        @Positive(message = "Biến thể sản phẩm không hợp lệ")
        private Long skuId;

        @NotNull(message = "Số lượng không được để trống")
        @Min(value = 1, message = "Số lượng phải lớn hơn 0")
        private Integer quantity;
    }
}

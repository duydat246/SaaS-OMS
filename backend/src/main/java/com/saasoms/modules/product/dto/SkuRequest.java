package com.saasoms.modules.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkuRequest {

    private Long id;

    @Size(max = 100, message = "Mã SKU tối đa 100 ký tự")
    private String skuCode;

    @Size(max = 200, message = "Tên biến thể tối đa 200 ký tự")
    private String variant;

    @NotNull(message = "Giá không được để trống")
    @DecimalMin(value = "0", message = "Giá phải lớn hơn hoặc bằng 0")
    @Digits(integer = 10, fraction = 2, message = "Giá tối đa 10 chữ số phần nguyên và 2 chữ số thập phân")
    private BigDecimal price;

    @NotNull(message = "Tồn kho không được để trống")
    @Min(value = 0, message = "Tồn kho phải lớn hơn hoặc bằng 0")
    private Integer stock;
}

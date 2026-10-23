package com.saasoms.modules.product.dto;

import com.saasoms.modules.product.entity.ProductStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductStatusRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private ProductStatus status;
}

package com.saasoms.modules.order.dto;

import com.saasoms.modules.order.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStatusRequest {
    @NotNull(message = "Status is required")
    private OrderStatus status;
}

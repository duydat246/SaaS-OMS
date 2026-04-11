package com.saasoms.modules.order.dto;

import java.math.BigDecimal;

public record OrderStatsResponse(
        long totalOrders,
        long pendingOrders,
        long deliveredOrders,
        BigDecimal deliveredRevenue
) {}

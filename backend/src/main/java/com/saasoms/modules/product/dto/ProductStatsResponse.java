package com.saasoms.modules.product.dto;

public record ProductStatsResponse(
        long totalProducts,
        long activeProducts,
        long inactiveProducts,
        long outOfStockCount,
        long totalStock
) {}

package com.saasoms.modules.storefront.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** Sản phẩm trong danh sách công khai. Chỉ báo còn/hết hàng, không trả số tồn. */
@Data
@Builder
public class StorefrontProductSummaryResponse {

    private Long id;
    private String name;
    private String category;
    private String imageUrl;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private boolean inStock;
}

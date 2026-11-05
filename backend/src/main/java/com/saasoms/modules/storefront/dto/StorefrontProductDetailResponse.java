package com.saasoms.modules.storefront.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Chi tiết sản phẩm cho khách. Biến thể chỉ báo còn/hết hàng, không có mã SKU hay số tồn. */
@Data
@Builder
public class StorefrontProductDetailResponse {

    private Long id;
    private String name;
    private String description;
    private String category;
    private String imageUrl;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private boolean inStock;
    private List<ImageItem> images;
    private List<SkuItem> skus;

    @Data
    @Builder
    public static class ImageItem {
        private Long id;
        private String url;
        private boolean primary;
    }

    @Data
    @Builder
    public static class SkuItem {
        private Long id;
        private String variant;
        private BigDecimal price;
        private boolean inStock;
    }
}

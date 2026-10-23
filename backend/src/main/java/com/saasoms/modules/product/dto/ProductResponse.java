package com.saasoms.modules.product.dto;

import com.saasoms.modules.product.entity.Product;
import com.saasoms.modules.product.entity.ProductStatus;
import com.saasoms.modules.product.entity.Sku;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Data
@Builder
public class ProductResponse {

    private Long id;
    private String name;
    private String description;
    private String category;
    private String platformSource;
    private String externalId;
    private String imageUrl;
    private ProductStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int skuCount;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private int totalStock;
    private List<SkuInfo> skus;
    private List<ProductImageResponse> images;

    @Data
    @Builder
    public static class SkuInfo {
        private Long id;
        private String skuCode;
        private String variant;
        private BigDecimal price;
        private Integer stock;
        private String externalId;
    }

    public static SkuInfo from(Sku sku) {
        return SkuInfo.builder()
                .id(sku.getId())
                .skuCode(sku.getSkuCode())
                .variant(sku.getVariant())
                .price(sku.getPrice())
                .stock(sku.getStock())
                .externalId(sku.getExternalId())
                .build();
    }

    public static ProductResponse from(Product product, List<Sku> skus) {
        return from(product, skus, List.of());
    }

    public static ProductResponse from(Product product,
                                       List<Sku> skus,
                                       List<ProductImageResponse> images) {
        List<Sku> safeSkus = skus == null ? List.of() : skus;
        List<ProductImageResponse> safeImages = images == null ? List.of() : images;

        BigDecimal minPrice = safeSkus.stream()
                .map(Sku::getPrice)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);

        BigDecimal maxPrice = safeSkus.stream()
                .map(Sku::getPrice)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        int totalStock = safeSkus.stream()
                .map(Sku::getStock)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .category(product.getCategory())
                .platformSource(product.getPlatformSource())
                .externalId(product.getExternalId())
                .imageUrl(resolveImageUrl(product, safeImages))
                .status(product.getStatus())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .skuCount(safeSkus.size())
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .totalStock(totalStock)
                .skus(safeSkus.stream().map(ProductResponse::from).toList())
                .images(safeImages)
                .build();
    }

    /**
     * URL ảnh chính: ưu tiên ảnh được đánh dấu primary, sau đó ảnh đầu tiên,
     * cuối cùng fallback về cột image_url cũ của sản phẩm.
     */
    private static String resolveImageUrl(Product product, List<ProductImageResponse> images) {
        if (!images.isEmpty()) {
            return images.stream()
                    .filter(ProductImageResponse::isPrimary)
                    .map(ProductImageResponse::getUrl)
                    .findFirst()
                    .orElseGet(() -> images.get(0).getUrl());
        }
        return product.getImageUrl();
    }
}

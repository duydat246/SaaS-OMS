package com.saasoms.modules.storefront.service;

import com.saasoms.common.exception.BusinessException;
import com.saasoms.common.media.LocalMediaStorage;
import com.saasoms.common.response.PageResponse;
import com.saasoms.common.util.SlugPolicy;
import com.saasoms.modules.product.entity.Product;
import com.saasoms.modules.product.entity.ProductImage;
import com.saasoms.modules.product.entity.ProductStatus;
import com.saasoms.modules.product.entity.Sku;
import com.saasoms.modules.product.repository.ProductImageRepository;
import com.saasoms.modules.product.repository.ProductRepository;
import com.saasoms.modules.product.repository.SkuRepository;
import com.saasoms.modules.shop.entity.Shop;
import com.saasoms.modules.shop.repository.ShopRepository;
import com.saasoms.modules.storefront.dto.StorefrontProductDetailResponse;
import com.saasoms.modules.storefront.dto.StorefrontProductSummaryResponse;
import com.saasoms.modules.storefront.dto.StorefrontShopResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Dữ liệu công khai cho khách. Không dùng TenantContext: chủ shop A đang đăng nhập xem shop B thì
 * context là A. Mọi query lấy shopId từ cửa hàng đã phân giải theo slug.
 */
@Service
@RequiredArgsConstructor
public class StorefrontService {

    private static final int MAX_SEARCH_LENGTH = 100;
    private static final String SHOP_ACTIVE = "ACTIVE";
    private static final String SHOP_NOT_FOUND_MESSAGE = "Không tìm thấy cửa hàng";
    private static final String PRODUCT_NOT_FOUND_MESSAGE = "Không tìm thấy sản phẩm";
    private static final String IMAGE_NOT_FOUND_MESSAGE = "Không tìm thấy ảnh";

    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;
    private final ProductImageRepository productImageRepository;
    private final LocalMediaStorage localMediaStorage;

    @Transactional(readOnly = true)
    public StorefrontShopResponse getShop(String slug) {
        return StorefrontShopResponse.from(requirePublishedShop(slug));
    }

    @Transactional(readOnly = true)
    public List<String> getCategories(String slug) {
        return productRepository.findPublishedCategories(requirePublishedShop(slug).getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<StorefrontProductSummaryResponse> getProducts(String slug, String search, String category,
                                                                      Pageable pageable) {
        Shop shop = requirePublishedShop(slug);
        Long shopId = shop.getId();

        Page<Product> products = productRepository.searchPublished(
                shopId, trimToNull(category), normalizeSearch(search), pageable);

        List<Long> productIds = products.getContent().stream().map(Product::getId).toList();
        if (productIds.isEmpty()) {
            return PageResponse.of(products.map(product -> toSummary(shop, product, List.of(), List.of())));
        }

        Map<Long, List<Sku>> skusByProduct = skuRepository.findByShopIdAndProductIdIn(shopId, productIds).stream()
                .collect(Collectors.groupingBy(sku -> sku.getProduct().getId()));
        // groupingBy giữ thứ tự sortOrder, id của query trong từng nhóm.
        Map<Long, List<ProductImage>> imagesByProduct = productImageRepository
                .findByShopIdAndProductIdInOrderBySortOrderAscIdAsc(shopId, productIds).stream()
                .collect(Collectors.groupingBy(image -> image.getProduct().getId()));

        return PageResponse.of(products.map(product -> toSummary(shop, product,
                skusByProduct.getOrDefault(product.getId(), List.of()),
                imagesByProduct.getOrDefault(product.getId(), List.of()))));
    }

    @Transactional(readOnly = true)
    public StorefrontProductDetailResponse getProduct(String slug, Long id) {
        Shop shop = requirePublishedShop(slug);
        Long shopId = shop.getId();

        Product product = productRepository.findByIdAndShopId(id, shopId)
                .filter(found -> found.getStatus() == ProductStatus.ACTIVE)
                .orElseThrow(() -> BusinessException.notFound(PRODUCT_NOT_FOUND_MESSAGE));

        List<Sku> skus = skuRepository.findByShopIdAndProductId(shopId, id).stream()
                .sorted(Comparator.comparing(Sku::getId))
                .toList();
        List<ProductImage> images = productImageRepository
                .findByShopIdAndProductIdOrderBySortOrderAscIdAsc(shopId, id);

        return StorefrontProductDetailResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .category(product.getCategory())
                .imageUrl(resolveImageUrl(shop, product, images))
                .minPrice(minPrice(skus))
                .maxPrice(maxPrice(skus))
                .inStock(skus.stream().anyMatch(StorefrontService::inStock))
                .images(images.stream().map(image -> toImageItem(shop, image)).toList())
                .skus(skus.stream().map(StorefrontService::toSkuItem).toList())
                .build();
    }

    /**
     * Chỉ tra DB và mở tệp, chưa đọc nội dung: tầng ghi response stream tệp sau khi method này trả về,
     * nên ảnh không bị nạp cả vào heap.
     */
    @Transactional(readOnly = true)
    public StorefrontImage getImage(String slug, Long imageId) {
        Long shopId = requirePublishedShop(slug).getId();

        ProductImage image = productImageRepository.findPublishedByIdAndShopId(imageId, shopId)
                .orElseThrow(() -> BusinessException.notFound(IMAGE_NOT_FOUND_MESSAGE));
        Resource resource = localMediaStorage.openResource(image.getStorageKey())
                .orElseThrow(() -> BusinessException.notFound(IMAGE_NOT_FOUND_MESSAGE));

        return new StorefrontImage(resource, image.getContentType());
    }

    public record StorefrontImage(Resource resource, String contentType) {
    }

    // ---------------------------------------------------------------- HELPER

    /** Điểm vào duy nhất của mọi đường đọc: slug sai định dạng không chạm DB. */
    private Shop requirePublishedShop(String slug) {
        if (!SlugPolicy.isValid(slug)) {
            throw BusinessException.notFound(SHOP_NOT_FOUND_MESSAGE);
        }
        return shopRepository.findBySlug(slug)
                .filter(shop -> SHOP_ACTIVE.equals(shop.getStatus()))
                .filter(shop -> Boolean.TRUE.equals(shop.getStorefrontEnabled()))
                .orElseThrow(() -> BusinessException.notFound(SHOP_NOT_FOUND_MESSAGE));
    }

    private static StorefrontProductSummaryResponse toSummary(Shop shop, Product product, List<Sku> skus,
                                                              List<ProductImage> images) {
        return StorefrontProductSummaryResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .category(product.getCategory())
                .imageUrl(resolveImageUrl(shop, product, images))
                .minPrice(minPrice(skus))
                .maxPrice(maxPrice(skus))
                .inStock(skus.stream().anyMatch(StorefrontService::inStock))
                .build();
    }

    private static StorefrontProductDetailResponse.ImageItem toImageItem(Shop shop, ProductImage image) {
        return StorefrontProductDetailResponse.ImageItem.builder()
                .id(image.getId())
                .url(imageUrl(shop, image))
                .primary(Boolean.TRUE.equals(image.getIsPrimary()))
                .build();
    }

    private static StorefrontProductDetailResponse.SkuItem toSkuItem(Sku sku) {
        return StorefrontProductDetailResponse.SkuItem.builder()
                .id(sku.getId())
                .variant(sku.getVariant())
                .price(sku.getPrice())
                .inStock(inStock(sku))
                .build();
    }

    /** Ảnh chính, rồi ảnh đầu tiên, rồi URL ngoài cũ. Đường dẫn tương đối cũ không dùng được cho khách. */
    private static String resolveImageUrl(Shop shop, Product product, List<ProductImage> images) {
        if (!images.isEmpty()) {
            ProductImage chosen = images.stream()
                    .filter(image -> Boolean.TRUE.equals(image.getIsPrimary()))
                    .findFirst()
                    .orElse(images.get(0));
            return imageUrl(shop, chosen);
        }
        String legacy = product.getImageUrl();
        boolean absolute = legacy != null && (legacy.startsWith("http://") || legacy.startsWith("https://"));
        return absolute ? legacy : null;
    }

    private static String imageUrl(Shop shop, ProductImage image) {
        return "/api/public/shops/" + shop.getSlug() + "/images/" + image.getId();
    }

    private static BigDecimal minPrice(List<Sku> skus) {
        return skus.stream().map(Sku::getPrice).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
    }

    private static BigDecimal maxPrice(List<Sku> skus) {
        return skus.stream().map(Sku::getPrice).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
    }

    private static boolean inStock(Sku sku) {
        return sku.getStock() != null && sku.getStock() > 0;
    }

    /** Cắt trước rồi mới escape: làm ngược có thể để lại ký tự thoát lẻ ở cuối chuỗi. */
    private static String normalizeSearch(String search) {
        String trimmed = trimToNull(search);
        if (trimmed == null) {
            return null;
        }
        String cut = trimmed.length() > MAX_SEARCH_LENGTH ? trimmed.substring(0, MAX_SEARCH_LENGTH) : trimmed;
        return escapeLike(cut);
    }

    /** Query dùng ESCAPE '!', nên dấu \ là ký tự thường và không cần escape. */
    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

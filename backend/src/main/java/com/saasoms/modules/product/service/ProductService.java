package com.saasoms.modules.product.service;

import com.saasoms.common.exception.BusinessException;
import com.saasoms.common.response.PageResponse;
import com.saasoms.common.tenant.TenantContext;
import com.saasoms.modules.product.dto.ProductRequest;
import com.saasoms.modules.product.dto.ProductResponse;
import com.saasoms.modules.product.dto.ProductStatsResponse;
import com.saasoms.modules.product.dto.SkuRequest;
import com.saasoms.modules.product.dto.UpdateProductStatusRequest;
import com.saasoms.modules.product.entity.Product;
import com.saasoms.modules.product.entity.ProductStatus;
import com.saasoms.modules.product.entity.Sku;
import com.saasoms.modules.product.repository.ProductRepository;
import com.saasoms.modules.product.repository.SkuRepository;
import com.saasoms.modules.shop.entity.Shop;
import com.saasoms.modules.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Set<String> ALLOWED_PLATFORMS = Set.of("MANUAL", "TIKTOK", "SHOPEE");
    private static final String DEFAULT_PLATFORM = "MANUAL";
    private static final Set<ProductStatus> VISIBLE_STATUSES =
            EnumSet.of(ProductStatus.ACTIVE, ProductStatus.INACTIVE);

    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;
    private final ShopRepository shopRepository;

    // ------------------------------------------------------------------ READ

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getProducts(ProductStatus status,
                                                     String category,
                                                     String platformSource,
                                                     String search,
                                                     Pageable pageable) {
        Long tenantId = requireTenant();

        Set<ProductStatus> statuses = status == null
                ? VISIBLE_STATUSES
                : EnumSet.of(status);

        Page<Product> page = productRepository.search(
                tenantId,
                statuses,
                trimToNull(category),
                trimToNull(platformSource),
                trimToNull(search),
                pageable);

        if (page.isEmpty()) {
            return PageResponse.of(page.map(p -> ProductResponse.from(p, List.of())));
        }

        Map<Long, List<Sku>> skusByProduct = groupByProductId(loadSkus(tenantId, page.getContent()));
        return PageResponse.of(page.map(p ->
                ProductResponse.from(p, skusByProduct.getOrDefault(p.getId(), List.of()))));
    }

    @Transactional(readOnly = true)
    public ProductStatsResponse getStats() {
        Long tenantId = requireTenant();

        long total = productRepository.countByShopIdAndStatusIn(tenantId, VISIBLE_STATUSES);
        long active = productRepository.countByShopIdAndStatus(tenantId, ProductStatus.ACTIVE);
        long inactive = productRepository.countByShopIdAndStatus(tenantId, ProductStatus.INACTIVE);
        long outOfStock = productRepository.countOutOfStock(tenantId, ProductStatus.ARCHIVED);
        Long stockSum = productRepository.sumTotalStock(tenantId, ProductStatus.ARCHIVED);
        long totalStock = stockSum == null ? 0L : stockSum;

        return new ProductStatsResponse(total, active, inactive, outOfStock, totalStock);
    }

    @Transactional(readOnly = true)
    public List<String> getCategories() {
        Long tenantId = requireTenant();
        return productRepository.findDistinctCategories(tenantId, ProductStatus.ARCHIVED);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        Long tenantId = requireTenant();
        Product product = findProduct(tenantId, id);
        return toResponse(tenantId, product);
    }

    // ----------------------------------------------------------------- WRITE

    @Transactional
    public ProductResponse createProduct(ProductRequest req) {
        Long tenantId = requireTenant();
        Shop shop = shopRepository.findById(tenantId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy cửa hàng"));

        String name = requireName(req.getName());
        requireSkus(req.getSkus());
        assertNoDuplicateInRequest(req.getSkus());

        Product product = Product.builder()
                .shop(shop)
                .name(name)
                .description(trimToNull(req.getDescription()))
                .category(trimToNull(req.getCategory()))
                .platformSource(normalizePlatformSource(req.getPlatformSource()))
                .imageUrl(trimToNull(req.getImageUrl()))
                .status(ProductStatus.ACTIVE)
                .build();

        Product savedProduct = productRepository.save(product);

        List<Sku> savedSkus = new ArrayList<>();
        for (SkuRequest skuReq : req.getSkus()) {
            String skuCode = trimToNull(skuReq.getSkuCode());
            if (skuCode == null) {
                skuCode = generateUniqueSkuCode(tenantId);
            }
            assertSkuCodeAvailable(tenantId, skuCode, null);

            Sku sku = Sku.builder()
                    .product(savedProduct)
                    .shop(shop)
                    .skuCode(skuCode)
                    .variant(trimToNull(skuReq.getVariant()))
                    .price(safePrice(skuReq.getPrice()))
                    .stock(safeStock(skuReq.getStock()))
                    .build();
            savedSkus.add(skuRepository.save(sku));
        }

        return ProductResponse.from(savedProduct, savedSkus);
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest req) {
        Long tenantId = requireTenant();
        Product product = findProduct(tenantId, id);

        if (product.getStatus() == ProductStatus.ARCHIVED) {
            throw BusinessException.badRequest(
                    "Sản phẩm đã bị xoá, hãy khôi phục trước khi sửa");
        }

        String name = requireName(req.getName());
        requireSkus(req.getSkus());

        List<Sku> existing = skuRepository.findByShopIdAndProductId(tenantId, id);
        Map<Long, Sku> existingById = existing.stream()
                .filter(sku -> sku.getId() != null)
                .collect(Collectors.toMap(Sku::getId, Function.identity(), (a, b) -> a));

        Set<Long> submittedIds = req.getSkus().stream()
                .map(SkuRequest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        List<Sku> toDelete = existing.stream()
                .filter(sku -> !submittedIds.contains(sku.getId()))
                .toList();

        // Tính mã cuối cùng cho từng dòng TRƯỚC khi ghi bất cứ thứ gì
        List<RequestedSku> requestedSkus = new ArrayList<>();
        for (SkuRequest skuReq : req.getSkus()) {
            Sku current = null;
            String finalCode = trimToNull(skuReq.getSkuCode());

            if (skuReq.getId() != null) {
                current = existingById.get(skuReq.getId());
                if (current == null) {
                    throw BusinessException.badRequest("SKU không thuộc sản phẩm này");
                }
                if (finalCode == null) {
                    finalCode = current.getSkuCode();
                }
            } else if (finalCode == null) {
                finalCode = generateUniqueSkuCode(tenantId);
            }

            requestedSkus.add(new RequestedSku(skuReq, current, finalCode));
        }

        assertNoDuplicateFinalCodes(requestedSkus);
        assertSkuCodesNotUsedByOtherProducts(tenantId, product.getId(), requestedSkus);

        // Kiểm tra SKU bị xoá có nằm trong đơn hàng không (làm TRƯỚC khi xoá)
        if (!toDelete.isEmpty()) {
            List<Long> deleteIds = toDelete.stream().map(Sku::getId).toList();
            List<Long> usedIds = skuRepository.findUsedSkuIds(tenantId, deleteIds);

            if (!usedIds.isEmpty()) {
                List<String> usedCodes = toDelete.stream()
                        .filter(sku -> usedIds.contains(sku.getId()))
                        .map(Sku::getSkuCode)
                        .toList();
                throw BusinessException.conflict(
                        "Không thể xoá biến thể đã dùng trong đơn hàng: "
                                + String.join(", ", usedCodes)
                                + ". Hãy giữ lại biến thể đó.");
            }

            skuRepository.deleteAll(toDelete);
            skuRepository.flush();
        }

        // Pha mã tạm để tránh vi phạm unique khi hoán đổi mã giữa các SKU
        List<Sku> toPark = requestedSkus.stream()
                .filter(item -> item.current() != null
                        && !Objects.equals(item.current().getSkuCode(), item.finalCode()))
                .map(RequestedSku::current)
                .toList();

        if (!toPark.isEmpty()) {
            toPark.forEach(sku -> sku.setSkuCode("TMP-" + UUID.randomUUID()));
            skuRepository.saveAll(toPark);
            skuRepository.flush();
        }

        // Áp dụng cập nhật / thêm mới
        product.setName(name);
        product.setDescription(trimToNull(req.getDescription()));
        product.setCategory(trimToNull(req.getCategory()));
        product.setPlatformSource(normalizePlatformSource(req.getPlatformSource()));
        product.setImageUrl(trimToNull(req.getImageUrl()));

        List<Sku> touched = new ArrayList<>();
        for (RequestedSku item : requestedSkus) {
            SkuRequest skuReq = item.request();

            if (item.current() != null) {
                Sku sku = item.current();
                sku.setSkuCode(item.finalCode());
                sku.setVariant(trimToNull(skuReq.getVariant()));
                sku.setPrice(safePrice(skuReq.getPrice()));
                sku.setStock(safeStock(skuReq.getStock()));
                touched.add(sku);
            } else {
                touched.add(Sku.builder()
                        .product(product)
                        .shop(product.getShop())
                        .skuCode(item.finalCode())
                        .variant(trimToNull(skuReq.getVariant()))
                        .price(safePrice(skuReq.getPrice()))
                        .stock(safeStock(skuReq.getStock()))
                        .build());
            }
        }

        skuRepository.saveAll(touched);
        skuRepository.flush();

        productRepository.save(product);
        return toResponse(tenantId, product);
    }

    @Transactional
    public ProductResponse updateStatus(Long id, UpdateProductStatusRequest req) {
        Long tenantId = requireTenant();

        if (req.getStatus() == null) {
            throw BusinessException.badRequest("Trạng thái không được để trống");
        }

        Product product = findProduct(tenantId, id);

        if (req.getStatus() == ProductStatus.ARCHIVED) {
            throw BusinessException.badRequest("Dùng chức năng xoá để lưu trữ sản phẩm");
        }

        if (product.getStatus() == ProductStatus.ARCHIVED) {
            throw BusinessException.badRequest(
                    "Sản phẩm đã bị xoá, hãy khôi phục trước khi đổi trạng thái");
        }

        product.setStatus(req.getStatus());
        productRepository.save(product);
        return toResponse(tenantId, product);
    }

    @Transactional
    public void archiveProduct(Long id) {
        Long tenantId = requireTenant();
        Product product = findProduct(tenantId, id);

        if (product.getStatus() != ProductStatus.ARCHIVED) {
            product.setStatus(ProductStatus.ARCHIVED);
            productRepository.save(product);
        }
    }

    @Transactional
    public ProductResponse restoreProduct(Long id) {
        Long tenantId = requireTenant();
        Product product = findProduct(tenantId, id);

        product.setStatus(ProductStatus.ACTIVE);
        productRepository.save(product);
        return toResponse(tenantId, product);
    }

    // ---------------------------------------------------------------- HELPER

    private Long requireTenant() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw BusinessException.forbidden("Không xác định được cửa hàng");
        }
        return tenantId;
    }

    private Product findProduct(Long tenantId, Long id) {
        return productRepository.findByIdAndShopId(id, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy sản phẩm"));
    }

    private ProductResponse toResponse(Long tenantId, Product product) {
        List<Sku> skus = skuRepository.findByShopIdAndProductId(tenantId, product.getId());
        return ProductResponse.from(product, skus);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String requireName(String rawName) {
        String name = trimToNull(rawName);
        if (name == null) {
            throw BusinessException.badRequest("Tên sản phẩm không được để trống");
        }
        return name;
    }

    private static void requireSkus(List<SkuRequest> skus) {
        if (skus == null || skus.isEmpty()) {
            throw BusinessException.badRequest("Sản phẩm phải có ít nhất 1 SKU");
        }
    }

    private List<Sku> loadSkus(Long tenantId, Collection<Product> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = products.stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .toList();

        if (productIds.isEmpty()) {
            return List.of();
        }

        return skuRepository.findByShopIdAndProductIdIn(tenantId, productIds);
    }

    private static Map<Long, List<Sku>> groupByProductId(List<Sku> skus) {
        if (skus == null || skus.isEmpty()) {
            return Map.of();
        }
        return skus.stream()
                .filter(sku -> sku.getProduct() != null && sku.getProduct().getId() != null)
                .collect(Collectors.groupingBy(sku -> sku.getProduct().getId()));
    }

    private String generateUniqueSkuCode(Long tenantId) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "SKU-" + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 8)
                    .toUpperCase(Locale.ROOT);
            if (!skuRepository.existsByShopIdAndSkuCode(tenantId, candidate)) {
                return candidate;
            }
        }
        throw BusinessException.conflict(
                "Không thể sinh mã SKU tự động, vui lòng thử lại hoặc nhập mã thủ công");
    }

    private void assertSkuCodeAvailable(Long tenantId, String skuCode, Long excludeSkuId) {
        String code = trimToNull(skuCode);
        if (code == null) {
            return;
        }

        boolean duplicated = excludeSkuId == null
                ? skuRepository.existsByShopIdAndSkuCode(tenantId, code)
                : skuRepository.existsByShopIdAndSkuCodeAndIdNot(tenantId, code, excludeSkuId);

        if (!duplicated) {
            return;
        }

        Sku existing = skuRepository.findByShopIdAndSkuCode(tenantId, code).orElse(null);
        if (existing != null
                && existing.getProduct() != null
                && existing.getProduct().getStatus() == ProductStatus.ARCHIVED) {
            throw BusinessException.conflict(
                    "Mã SKU \"" + code + "\" đang thuộc sản phẩm đã xoá (ID "
                            + existing.getProduct().getId()
                            + "). Hãy khôi phục sản phẩm đó hoặc dùng mã khác.");
        }

        throw BusinessException.conflict("Mã SKU \"" + code + "\" đã tồn tại trong cửa hàng.");
    }

    private void assertNoDuplicateInRequest(List<SkuRequest> skus) {
        if (skus == null || skus.isEmpty()) {
            return;
        }

        Set<String> seen = new HashSet<>();
        for (SkuRequest skuReq : skus) {
            String code = trimToNull(skuReq.getSkuCode());
            if (code == null) {
                continue;
            }
            if (!seen.add(code.toLowerCase(Locale.ROOT))) {
                throw BusinessException.badRequest(
                        "Mã SKU \"" + code + "\" bị lặp trong danh sách biến thể.");
            }
        }
    }

    private static void assertNoDuplicateFinalCodes(List<RequestedSku> requestedSkus) {
        Set<String> seen = new HashSet<>();
        for (RequestedSku item : requestedSkus) {
            String code = item.finalCode();
            if (!seen.add(code.toLowerCase(Locale.ROOT))) {
                throw BusinessException.badRequest(
                        "Mã SKU \"" + code + "\" bị lặp trong danh sách biến thể.");
            }
        }
    }

    private void assertSkuCodesNotUsedByOtherProducts(Long tenantId,
                                                      Long productId,
                                                      List<RequestedSku> requestedSkus) {
        if (requestedSkus.isEmpty()) {
            return;
        }

        List<String> finalCodes = requestedSkus.stream()
                .map(RequestedSku::finalCode)
                .distinct()
                .toList();

        List<Sku> conflicting = skuRepository.findByShopIdAndSkuCodeIn(tenantId, finalCodes);
        for (Sku sku : conflicting) {
            if (sku.getProduct() == null || Objects.equals(sku.getProduct().getId(), productId)) {
                continue;
            }

            String code = sku.getSkuCode();
            if (sku.getProduct().getStatus() == ProductStatus.ARCHIVED) {
                throw BusinessException.conflict(
                        "Mã SKU \"" + code + "\" đang thuộc sản phẩm đã xoá (ID "
                                + sku.getProduct().getId()
                                + "). Hãy khôi phục sản phẩm đó hoặc dùng mã khác.");
            }

            throw BusinessException.conflict(
                    "Mã SKU \"" + code + "\" đã tồn tại trong cửa hàng.");
        }
    }

    private record RequestedSku(SkuRequest request, Sku current, String finalCode) {
    }

    private String normalizePlatformSource(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return DEFAULT_PLATFORM;
        }

        String normalized = value.toUpperCase(Locale.ROOT);
        if (!ALLOWED_PLATFORMS.contains(normalized)) {
            throw BusinessException.badRequest(
                    "Nền tảng nguồn chỉ nhận MANUAL, TIKTOK hoặc SHOPEE");
        }
        return normalized;
    }

    private static BigDecimal safePrice(BigDecimal price) {
        return price == null ? BigDecimal.ZERO : price;
    }

    private static int safeStock(Integer stock) {
        return stock == null ? 0 : stock;
    }
}

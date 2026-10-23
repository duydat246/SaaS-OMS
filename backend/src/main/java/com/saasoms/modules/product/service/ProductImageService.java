package com.saasoms.modules.product.service;

import com.saasoms.common.exception.BusinessException;
import com.saasoms.common.media.LocalMediaStorage;
import com.saasoms.common.media.MediaUrlSigner;
import com.saasoms.modules.product.dto.ProductImageResponse;
import com.saasoms.modules.product.entity.Product;
import com.saasoms.modules.product.entity.ProductImage;
import com.saasoms.modules.product.repository.ProductImageRepository;
import com.saasoms.modules.product.repository.ProductRepository;
import com.saasoms.modules.shop.entity.Shop;
import com.saasoms.modules.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_IMAGES_PER_PRODUCT = 10;
    private static final int DRAFT_RETENTION_HOURS = 24;
    private static final String INVALID_TYPE_MESSAGE = "Chỉ chấp nhận ảnh JPEG, PNG, WEBP hoặc GIF";
    private static final String INVALID_LINK_MESSAGE = "Liên kết ảnh không hợp lệ hoặc đã hết hạn";
    private static final String IMAGE_NOT_FOUND_MESSAGE = "Không tìm thấy ảnh";
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final ProductImageRepository productImageRepository;
    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final LocalMediaStorage localMediaStorage;
    private final MediaUrlSigner mediaUrlSigner;

    // ---------------------------------------------------------------- UPLOAD

    /**
     * Nhận ảnh tải lên và lưu thành ảnh nháp (chưa gắn sản phẩm).
     */
    @Transactional
    public ProductImageResponse upload(Long tenantId, MultipartFile file) {
        if (tenantId == null) {
            throw BusinessException.forbidden("Không xác định được cửa hàng");
        }
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("Tệp ảnh rỗng");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw BusinessException.badRequest("Ảnh tối đa 5MB");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw BusinessException.badRequest(INVALID_TYPE_MESSAGE);
        }

        byte[] content = readBytes(file);
        if (content.length == 0) {
            throw BusinessException.badRequest("Tệp ảnh rỗng");
        }
        if (content.length > MAX_FILE_SIZE) {
            throw BusinessException.badRequest("Ảnh tối đa 5MB");
        }
        if (!matchesSignature(content, contentType)) {
            throw BusinessException.badRequest(INVALID_TYPE_MESSAGE);
        }

        String extension = extensionFor(contentType);
        Shop shop = shopRepository.findById(tenantId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy cửa hàng"));
        String storageKey = localMediaStorage.store(tenantId, content, extension);

        ProductImage image = ProductImage.builder()
                .shop(shop)
                .product(null)
                .storageKey(storageKey)
                .fileName(resolveFileName(file.getOriginalFilename(), extension))
                .contentType(contentType)
                .fileSize((long) content.length)
                .sortOrder(0)
                .isPrimary(false)
                .build();

        try {
            return toResponse(tenantId, productImageRepository.save(image));
        } catch (RuntimeException ex) {
            // Không để lại tệp mồ côi nếu ghi DB thất bại.
            localMediaStorage.delete(storageKey);
            throw ex;
        }
    }

    // ---------------------------------------------------------------- DELETE

    /**
     * Xoá một ảnh của cửa hàng hiện tại. Giữ ảnh chính luôn hợp lệ.
     */
    @Transactional
    public void delete(Long tenantId, Long imageId) {
        if (tenantId == null) {
            throw BusinessException.forbidden("Không xác định được cửa hàng");
        }

        ProductImage image = productImageRepository.findByIdAndShopId(imageId, tenantId)
                .orElseThrow(() -> BusinessException.notFound(IMAGE_NOT_FOUND_MESSAGE));

        Product product = image.getProduct();
        Long productId = product == null ? null : product.getId();
        boolean wasPrimary = Boolean.TRUE.equals(image.getIsPrimary());
        String storageKey = image.getStorageKey();

        productImageRepository.delete(image);
        productImageRepository.flush();

        if (productId != null) {
            List<ProductImage> remaining = productImageRepository
                    .findByShopIdAndProductIdOrderBySortOrderAscIdAsc(tenantId, productId);

            if (remaining.isEmpty()) {
                productRepository.findByIdAndShopId(productId, tenantId).ifPresent(managed -> {
                    managed.setImageUrl(null);
                    productRepository.save(managed);
                });
            } else if (wasPrimary) {
                ProductImage next = remaining.get(0);
                next.setIsPrimary(true);
                productImageRepository.save(next);
            }
        }

        deleteFileAfterCommit(storageKey);
    }

    // ------------------------------------------------------------------ SYNC

    /**
     * Đồng bộ danh sách ảnh của sản phẩm theo danh sách id client gửi lên.
     * {@code imageIds == null} nghĩa là giữ nguyên ảnh hiện có.
     */
    @Transactional
    public List<ProductImageResponse> syncProductImages(Long tenantId,
                                                        Product product,
                                                        List<Long> imageIds,
                                                        Long primaryImageId) {
        if (tenantId == null) {
            throw BusinessException.forbidden("Không xác định được cửa hàng");
        }
        if (product == null || product.getId() == null) {
            throw BusinessException.badRequest("Sản phẩm chưa được lưu, không thể gắn ảnh");
        }

        if (imageIds == null) {
            return toResponses(tenantId, findByProductId(tenantId, product.getId()));
        }

        List<Long> requested = normalizeImageIds(imageIds);
        if (primaryImageId != null && !requested.contains(primaryImageId)) {
            throw BusinessException.badRequest("Ảnh chính phải nằm trong danh sách ảnh");
        }

        List<ProductImage> selected = loadSelectable(tenantId, product, requested);

        List<ProductImage> current = productImageRepository
                .findByShopIdAndProductIdOrderBySortOrderAscIdAsc(tenantId, product.getId());

        Set<Long> keepIds = new HashSet<>(requested);
        List<ProductImage> removed = current.stream()
                .filter(item -> item.getId() != null && !keepIds.contains(item.getId()))
                .toList();

        if (!removed.isEmpty()) {
            removed.forEach(item -> deleteFileAfterCommit(item.getStorageKey()));
            productImageRepository.deleteAll(removed);
            productImageRepository.flush();
        }

        Long effectivePrimaryId = primaryImageId != null
                ? primaryImageId
                : (requested.isEmpty() ? null : requested.get(0));

        for (int index = 0; index < selected.size(); index++) {
            ProductImage image = selected.get(index);
            image.setProduct(product);
            image.setSortOrder(index);
            image.setIsPrimary(Objects.equals(image.getId(), effectivePrimaryId));
        }

        if (!selected.isEmpty()) {
            productImageRepository.saveAll(selected);
            productImageRepository.flush();
        }

        return toResponses(tenantId, selected);
    }

    // ------------------------------------------------------------------ READ

    @Transactional(readOnly = true)
    public List<ProductImage> findByProductId(Long tenantId, Long productId) {
        if (productId == null) {
            return List.of();
        }
        return productImageRepository
                .findByShopIdAndProductIdOrderBySortOrderAscIdAsc(tenantId, productId);
    }

    /**
     * Nạp ảnh của nhiều sản phẩm bằng một query duy nhất (chống N+1).
     */
    @Transactional(readOnly = true)
    public Map<Long, List<ProductImage>> findByProductIds(Long tenantId, Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }

        List<Long> ids = productIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        List<ProductImage> images = productImageRepository
                .findByShopIdAndProductIdInOrderBySortOrderAscIdAsc(tenantId, ids);

        Map<Long, List<ProductImage>> grouped = new LinkedHashMap<>();
        for (ProductImage image : images) {
            Product product = image.getProduct();
            if (product == null || product.getId() == null) {
                continue;
            }
            grouped.computeIfAbsent(product.getId(), key -> new ArrayList<>()).add(image);
        }
        return grouped;
    }

    @Transactional(readOnly = true)
    public List<ProductImageResponse> listProductImages(Long tenantId, Long productId) {
        if (tenantId == null) {
            throw BusinessException.forbidden("Không xác định được cửa hàng");
        }
        if (productId == null || !productRepository.existsByIdAndShopId(productId, tenantId)) {
            throw BusinessException.notFound("Không tìm thấy sản phẩm");
        }
        return toResponses(tenantId, findByProductId(tenantId, productId));
    }

    /**
     * Phục vụ ảnh theo URL đã ký (không cần JWT).
     */
    @Transactional(readOnly = true)
    public MediaContent serveSignedImage(Long imageId,
                                         long tenantId,
                                         long expiresAtEpochSecond,
                                         String signature) {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> BusinessException.notFound(IMAGE_NOT_FOUND_MESSAGE));

        Shop shop = image.getShop();
        Long ownerTenantId = shop == null ? null : shop.getId();
        if (ownerTenantId == null || ownerTenantId.longValue() != tenantId) {
            throw BusinessException.forbidden(INVALID_LINK_MESSAGE);
        }
        if (!mediaUrlSigner.verify(imageId, ownerTenantId, expiresAtEpochSecond, signature)) {
            throw BusinessException.forbidden(INVALID_LINK_MESSAGE);
        }

        byte[] content = localMediaStorage.read(image.getStorageKey());
        return new MediaContent(content, image.getContentType(), image.getFileName());
    }

    public List<ProductImageResponse> toResponses(Long tenantId, List<ProductImage> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<ProductImageResponse> responses = new ArrayList<>(images.size());
        for (ProductImage image : images) {
            responses.add(toResponse(tenantId, image));
        }
        return responses;
    }

    public ProductImageResponse toResponse(Long tenantId, ProductImage image) {
        long expiresAt = mediaUrlSigner.expiresAt();
        String url = buildUrl(tenantId, image.getId(), expiresAt);

        return ProductImageResponse.builder()
                .id(image.getId())
                .url(url)
                .fileName(image.getFileName())
                .contentType(image.getContentType())
                .fileSize(image.getFileSize())
                .sortOrder(image.getSortOrder())
                .primary(Boolean.TRUE.equals(image.getIsPrimary()))
                .build();
    }

    // --------------------------------------------------------------- CLEAN UP

    /**
     * Dọn ảnh nháp chưa gắn sản phẩm sau 24 giờ, chạy mỗi 6 giờ.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    @Transactional
    public void cleanupDraftImages() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(DRAFT_RETENTION_HOURS);
        List<ProductImage> drafts = productImageRepository.findByProductIsNullAndCreatedAtBefore(cutoff);
        if (drafts.isEmpty()) {
            return;
        }

        drafts.forEach(image -> deleteFileAfterCommit(image.getStorageKey()));
        productImageRepository.deleteAll(drafts);
        productImageRepository.flush();
        log.info("Đã dọn {} ảnh nháp quá hạn", drafts.size());
    }

    // ---------------------------------------------------------------- HELPER

    public record MediaContent(byte[] content, String contentType, String fileName) {
    }

    private String buildUrl(Long tenantId, Long imageId, long expiresAt) {
        String signature = mediaUrlSigner.sign(imageId, tenantId, expiresAt);
        return "/api/media/products/" + imageId
                + "?t=" + tenantId
                + "&e=" + expiresAt
                + "&s=" + signature;
    }

    private List<ProductImage> loadSelectable(Long tenantId, Product product, List<Long> requested) {
        if (requested.isEmpty()) {
            return List.of();
        }

        List<ProductImage> loaded = productImageRepository.findByShopIdAndIdIn(tenantId, requested);
        Map<Long, ProductImage> byId = loaded.stream()
                .filter(image -> image.getId() != null)
                .collect(Collectors.toMap(ProductImage::getId, image -> image, (a, b) -> a));

        List<ProductImage> selected = new ArrayList<>(requested.size());
        for (Long imageId : requested) {
            ProductImage image = byId.get(imageId);
            if (image == null) {
                throw BusinessException.notFound("Không tìm thấy ảnh " + imageId);
            }
            Product owner = image.getProduct();
            if (owner != null && owner.getId() != null && !Objects.equals(owner.getId(), product.getId())) {
                throw BusinessException.notFound("Không tìm thấy ảnh " + imageId);
            }
            selected.add(image);
        }
        return selected;
    }

    private static List<Long> normalizeImageIds(List<Long> imageIds) {
        Set<Long> unique = new LinkedHashSet<>();
        for (Long id : imageIds) {
            if (id == null) {
                continue;
            }
            if (!unique.add(id)) {
                throw BusinessException.badRequest("Ảnh bị lặp trong danh sách");
            }
        }
        if (unique.size() > MAX_IMAGES_PER_PRODUCT) {
            throw BusinessException.badRequest("Tối đa 10 ảnh mỗi sản phẩm");
        }
        return List.copyOf(unique);
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw BusinessException.badRequest("Không đọc được tệp ảnh, vui lòng thử lại");
        }
    }

    private static String normalizeContentType(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        int semicolon = value.indexOf(';');
        return semicolon >= 0 ? value.substring(0, semicolon).trim() : value;
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
    }

    private static boolean matchesSignature(byte[] content, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> startsWith(content, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(content, 0x89, 0x50, 0x4E, 0x47);
            case "image/gif" -> startsWith(content, 0x47, 0x49, 0x46, 0x38);
            case "image/webp" -> startsWith(content, 0x52, 0x49, 0x46, 0x46)
                    && startsWithAt(content, 8, 0x57, 0x45, 0x42, 0x50);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] content, int... prefix) {
        return startsWithAt(content, 0, prefix);
    }

    private static boolean startsWithAt(byte[] content, int offset, int... prefix) {
        if (content == null || content.length < offset + prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if ((content[offset + index] & 0xFF) != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static String resolveFileName(String originalFileName, String extension) {
        String fallback = "image." + extension;
        if (originalFileName == null) {
            return fallback;
        }

        String cleaned = originalFileName.trim();
        int slash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        if (slash >= 0) {
            cleaned = cleaned.substring(slash + 1);
        }
        cleaned = cleaned.replaceAll("\\p{Cntrl}", "").trim();

        if (cleaned.isEmpty()) {
            return fallback;
        }
        return cleaned.length() > 255 ? cleaned.substring(0, 255) : cleaned;
    }

    /**
     * Xoá tệp sau khi transaction commit; nếu không có transaction thì xoá ngay.
     */
    private void deleteFileAfterCommit(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    localMediaStorage.delete(storageKey);
                }
            });
        } else {
            localMediaStorage.delete(storageKey);
        }
    }
}

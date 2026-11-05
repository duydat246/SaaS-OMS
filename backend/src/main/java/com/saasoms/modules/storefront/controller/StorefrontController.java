package com.saasoms.modules.storefront.controller;

import com.saasoms.common.response.ApiResponse;
import com.saasoms.common.response.PageResponse;
import com.saasoms.modules.storefront.dto.StorefrontProductDetailResponse;
import com.saasoms.modules.storefront.dto.StorefrontProductSummaryResponse;
import com.saasoms.modules.storefront.dto.StorefrontShopResponse;
import com.saasoms.modules.storefront.service.StorefrontService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * API công khai cho khách: chỉ GET, không JWT. Cửa hàng xác định từ slug, không từ token.
 */
@RestController
@RequestMapping("/api/public/shops/{slug}")
@RequiredArgsConstructor
@Tag(name = "Storefront")
public class StorefrontController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "createdAt", "updatedAt");
    private static final String DEFAULT_SORT_FIELD = "updatedAt";
    // Viết tay vì CacheControl của Spring sinh "max-age=3600, public", khác thứ tự trong hợp đồng API.
    private static final String IMAGE_CACHE_CONTROL = "public, max-age=3600";

    private final StorefrontService storefrontService;

    @GetMapping
    @Operation(summary = "Thông tin công khai của cửa hàng")
    public ApiResponse<StorefrontShopResponse> shop(@PathVariable String slug) {
        return ApiResponse.ok(storefrontService.getShop(slug));
    }

    @GetMapping("/categories")
    @Operation(summary = "Danh mục của sản phẩm đang bán")
    public ApiResponse<List<String>> categories(@PathVariable String slug) {
        return ApiResponse.ok(storefrontService.getCategories(slug));
    }

    @GetMapping("/products")
    @Operation(summary = "Danh sách sản phẩm đang bán có lọc, tìm kiếm và phân trang")
    public ApiResponse<PageResponse<StorefrontProductSummaryResponse>> products(
            @PathVariable String slug,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort) {

        int safeSize = Math.min(Math.max(size, 1), 100);
        // Offset vượt Integer.MAX_VALUE sẽ lỗi khi query; kẹp lại để trả trang rỗng kèm totalPages thật.
        int safePage = Math.min(Math.max(page, 0), Integer.MAX_VALUE / safeSize);
        Pageable pageable = PageRequest.of(safePage, safeSize, parseSort(sort));

        return ApiResponse.ok(storefrontService.getProducts(slug, search, category, pageable));
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "Chi tiết sản phẩm đang bán")
    public ApiResponse<StorefrontProductDetailResponse> product(@PathVariable String slug, @PathVariable Long id) {
        return ApiResponse.ok(storefrontService.getProduct(slug, id));
    }

    @GetMapping("/images/{imageId}")
    @Operation(summary = "Tải nội dung ảnh sản phẩm đang bán")
    public ResponseEntity<Resource> image(@PathVariable String slug, @PathVariable Long imageId) {
        StorefrontService.StorefrontImage image = storefrontService.getImage(slug, imageId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(parseMediaType(image.contentType()));
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl(IMAGE_CACHE_CONTROL);

        return ResponseEntity.ok().headers(headers).body(image.resource());
    }

    private static MediaType parseMediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (RuntimeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static Sort parseSort(String sort) {
        String[] parts = sort.trim().split(",");

        String field = parts.length == 0 ? "" : parts[0].trim();
        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            return withTieBreaker(Sort.by(Sort.Direction.DESC, DEFAULT_SORT_FIELD));
        }

        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return withTieBreaker(Sort.by(direction, field));
    }

    /** Nhiều sản phẩm trùng giá trị sắp xếp thì thứ tự phải cố định, nếu không các trang sẽ lặp hoặc sót dòng. */
    private static Sort withTieBreaker(Sort sort) {
        return sort.and(Sort.by(Sort.Direction.DESC, "id"));
    }
}

package com.saasoms.modules.product.controller;

import com.saasoms.common.response.ApiResponse;
import com.saasoms.common.response.PageResponse;
import com.saasoms.modules.product.dto.ProductRequest;
import com.saasoms.modules.product.dto.ProductResponse;
import com.saasoms.modules.product.dto.ProductStatsResponse;
import com.saasoms.modules.product.dto.UpdateProductStatusRequest;
import com.saasoms.modules.product.entity.ProductStatus;
import com.saasoms.modules.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products")
public class ProductController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "createdAt", "updatedAt");
    private static final String DEFAULT_SORT_FIELD = "updatedAt";

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Danh sách sản phẩm có lọc, tìm kiếm và phân trang")
    public ApiResponse<PageResponse<ProductResponse>> list(
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String platformSource,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize, parseSort(sort));

        return ApiResponse.ok(
                productService.getProducts(status, category, platformSource, search, pageable));
    }

    @GetMapping("/stats")
    @Operation(summary = "Thống kê sản phẩm của cửa hàng hiện tại")
    public ApiResponse<ProductStatsResponse> stats() {
        return ApiResponse.ok(productService.getStats());
    }

    @GetMapping("/categories")
    @Operation(summary = "Danh sách danh mục sản phẩm đang sử dụng")
    public ApiResponse<List<String>> categories() {
        return ApiResponse.ok(productService.getCategories());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết sản phẩm kèm danh sách SKU")
    public ApiResponse<ProductResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(productService.getProduct(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo sản phẩm kèm danh sách SKU")
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductRequest req) {
        return ApiResponse.ok(productService.createProduct(req), "Đã tạo sản phẩm");
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật sản phẩm và danh sách SKU")
    public ApiResponse<ProductResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody ProductRequest req) {
        return ApiResponse.ok(productService.updateProduct(id, req), "Đã cập nhật sản phẩm");
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Đổi trạng thái sản phẩm (chỉ Đang bán / Ngừng bán)")
    public ApiResponse<ProductResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductStatusRequest req) {
        return ApiResponse.ok(productService.updateStatus(id, req), "Đã cập nhật trạng thái");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xoá mềm sản phẩm (chuyển sang trạng thái Đã xoá)")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productService.archiveProduct(id);
        return ApiResponse.ok(null, "Đã xoá sản phẩm");
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Khôi phục sản phẩm đã xoá")
    public ApiResponse<ProductResponse> restore(@PathVariable Long id) {
        return ApiResponse.ok(productService.restoreProduct(id), "Đã khôi phục sản phẩm");
    }

    private static Sort parseSort(String sort) {
        String raw = (sort == null || sort.isBlank()) ? DEFAULT_SORT_FIELD : sort.trim();
        String[] parts = raw.split(",");

        String field = parts[0].trim();
        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            return Sort.by(Sort.Direction.DESC, DEFAULT_SORT_FIELD);
        }

        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())) {
            direction = Sort.Direction.ASC;
        }

        return Sort.by(direction, field);
    }
}

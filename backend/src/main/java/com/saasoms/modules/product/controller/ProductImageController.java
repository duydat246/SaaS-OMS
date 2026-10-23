package com.saasoms.modules.product.controller;

import com.saasoms.common.exception.BusinessException;
import com.saasoms.common.response.ApiResponse;
import com.saasoms.common.tenant.TenantContext;
import com.saasoms.modules.product.dto.ProductImageResponse;
import com.saasoms.modules.product.service.ProductImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Product Images")
public class ProductImageController {

    private final ProductImageService productImageService;

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tải ảnh sản phẩm lên (ảnh nháp)")
    public ApiResponse<ProductImageResponse> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(productImageService.upload(requireTenant(), file), "Đã tải ảnh lên");
    }

    @GetMapping("/{productId}/images")
    @Operation(summary = "Danh sách ảnh của một sản phẩm theo thứ tự hiển thị")
    public ApiResponse<List<ProductImageResponse>> list(@PathVariable Long productId) {
        return ApiResponse.ok(productImageService.listProductImages(requireTenant(), productId));
    }

    @DeleteMapping("/images/{imageId}")
    @Operation(summary = "Xoá ảnh sản phẩm")
    public ApiResponse<Void> delete(@PathVariable Long imageId) {
        productImageService.delete(requireTenant(), imageId);
        return ApiResponse.ok(null, "Đã xoá ảnh");
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw BusinessException.forbidden("Không xác định được cửa hàng");
        }
        return tenantId;
    }
}

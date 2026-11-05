package com.saasoms.modules.shop.controller;

import com.saasoms.common.response.ApiResponse;
import com.saasoms.modules.shop.dto.ShopResponse;
import com.saasoms.modules.shop.dto.UpdateStorefrontRequest;
import com.saasoms.modules.shop.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
@Tag(name = "Shop")
public class ShopController {

    private final ShopService shopService;

    @GetMapping
    @Operation(summary = "Thông tin cửa hàng hiện tại và cài đặt trang bán hàng")
    public ApiResponse<ShopResponse> get() {
        return ApiResponse.ok(shopService.getCurrentShop());
    }

    @PutMapping("/storefront")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Bật/tắt trang bán hàng, đặt địa chỉ và số liên hệ (chỉ chủ cửa hàng)")
    public ApiResponse<ShopResponse> updateStorefront(@Valid @RequestBody UpdateStorefrontRequest req) {
        return ApiResponse.ok(shopService.updateStorefront(req), "Đã lưu cài đặt trang bán hàng");
    }
}

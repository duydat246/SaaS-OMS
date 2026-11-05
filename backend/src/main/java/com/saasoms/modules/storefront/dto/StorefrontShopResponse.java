package com.saasoms.modules.storefront.dto;

import com.saasoms.modules.shop.entity.Shop;
import lombok.Builder;
import lombok.Data;

/** Thông tin cửa hàng cho khách. Không lộ id, trạng thái hay dữ liệu nội bộ. */
@Data
@Builder
public class StorefrontShopResponse {

    private String slug;
    private String name;
    private String contactPhone;

    public static StorefrontShopResponse from(Shop shop) {
        return StorefrontShopResponse.builder()
                .slug(shop.getSlug())
                .name(shop.getName())
                .contactPhone(shop.getContactPhone())
                .build();
    }
}

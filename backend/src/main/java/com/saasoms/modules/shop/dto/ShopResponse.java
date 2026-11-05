package com.saasoms.modules.shop.dto;

import com.saasoms.modules.shop.entity.Shop;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShopResponse {

    private String name;
    private String slug;
    private boolean storefrontEnabled;
    private String contactPhone;

    public static ShopResponse from(Shop shop) {
        return ShopResponse.builder()
                .name(shop.getName())
                .slug(shop.getSlug())
                .storefrontEnabled(Boolean.TRUE.equals(shop.getStorefrontEnabled()))
                .contactPhone(shop.getContactPhone())
                .build();
    }
}

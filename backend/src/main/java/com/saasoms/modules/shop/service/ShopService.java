package com.saasoms.modules.shop.service;

import com.saasoms.common.exception.BusinessException;
import com.saasoms.common.tenant.TenantContext;
import com.saasoms.modules.shop.dto.ShopResponse;
import com.saasoms.modules.shop.dto.UpdateStorefrontRequest;
import com.saasoms.modules.shop.entity.Shop;
import com.saasoms.modules.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopRepository shopRepository;

    @Transactional(readOnly = true)
    public ShopResponse getCurrentShop() {
        return ShopResponse.from(findShop(requireTenant()));
    }

    @Transactional
    public ShopResponse updateStorefront(UpdateStorefrontRequest req) {
        Long tenantId = requireTenant();
        Shop shop = findShop(tenantId);

        if (shopRepository.existsBySlugAndIdNot(req.getSlug(), tenantId)) {
            throw BusinessException.conflict("Địa chỉ cửa hàng đã được sử dụng");
        }

        shop.setStorefrontEnabled(req.getStorefrontEnabled());
        shop.setSlug(req.getSlug());
        String phone = req.getContactPhone();
        shop.setContactPhone(phone == null || phone.isEmpty() ? null : phone);

        // Flush ngay để vi phạm unique khi hai request đua nhau đi qua handler chung thành 409.
        return ShopResponse.from(shopRepository.saveAndFlush(shop));
    }

    private Shop findShop(Long tenantId) {
        return shopRepository.findById(tenantId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy cửa hàng"));
    }

    private static Long requireTenant() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw BusinessException.forbidden("Không xác định được cửa hàng");
        }
        return tenantId;
    }
}

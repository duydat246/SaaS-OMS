package com.saasoms.modules.shop.service;

import com.saasoms.common.exception.BusinessException;
import com.saasoms.common.tenant.TenantContext;
import com.saasoms.modules.shop.dto.ShopResponse;
import com.saasoms.modules.shop.dto.UpdateStorefrontRequest;
import com.saasoms.modules.shop.entity.Shop;
import com.saasoms.modules.shop.repository.ShopRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long OTHER_TENANT_ID = 2L;

    @Mock
    private ShopRepository shopRepository;

    @InjectMocks
    private ShopService shopService;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getCurrentShop_usesTenantFromContext() {
        when(shopRepository.findById(TENANT_ID)).thenReturn(Optional.of(shop()));

        ShopResponse response = shopService.getCurrentShop();

        assertThat(response.getName()).isEqualTo("Cửa hàng A");
        assertThat(response.getSlug()).isEqualTo("cua-hang-a");
        assertThat(response.isStorefrontEnabled()).isFalse();
        assertThat(response.getContactPhone()).isNull();
        verify(shopRepository, never()).findById(OTHER_TENANT_ID);
    }

    @Test
    void getCurrentShop_noTenant_throws403() {
        TenantContext.clear();

        BusinessException ex = assertThrows(BusinessException.class, () -> shopService.getCurrentShop());

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(shopRepository);
    }

    @Test
    void getCurrentShop_notFound_throws404() {
        when(shopRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> shopService.getCurrentShop());

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("Không tìm thấy cửa hàng");
    }

    @Test
    void updateStorefront_noTenant_throws403() {
        TenantContext.clear();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> shopService.updateStorefront(request(true, "cua-hang-moi", null)));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(shopRepository);
    }

    @Test
    void updateStorefront_otherTenantContext_updatesOnlyContextShop() {
        TenantContext.set(OTHER_TENANT_ID);
        Shop otherShop = Shop.builder().id(OTHER_TENANT_ID).name("Cửa hàng B").slug("cua-hang-b")
                .platform("MANUAL").build();
        when(shopRepository.findById(OTHER_TENANT_ID)).thenReturn(Optional.of(otherShop));
        when(shopRepository.existsBySlugAndIdNot("cua-hang-b-moi", OTHER_TENANT_ID)).thenReturn(false);
        when(shopRepository.saveAndFlush(otherShop)).thenReturn(otherShop);

        shopService.updateStorefront(request(true, "cua-hang-b-moi", "0901234567"));

        ArgumentCaptor<Shop> saved = ArgumentCaptor.forClass(Shop.class);
        verify(shopRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(OTHER_TENANT_ID);
        assertThat(saved.getValue().getSlug()).isEqualTo("cua-hang-b-moi");
        assertThat(saved.getValue().getStorefrontEnabled()).isTrue();
        assertThat(saved.getValue().getContactPhone()).isEqualTo("0901234567");
        verify(shopRepository, never()).findById(TENANT_ID);
    }

    @Test
    void updateStorefront_shopNotFound_throws404WithoutWriting() {
        when(shopRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> shopService.updateStorefront(request(true, "cua-hang-a", null)));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(shopRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateStorefront_duplicateSlug_throws409WithoutWriting() {
        Shop shop = shop();
        when(shopRepository.findById(TENANT_ID)).thenReturn(Optional.of(shop));
        when(shopRepository.existsBySlugAndIdNot("da-co", TENANT_ID)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> shopService.updateStorefront(request(true, "da-co", "0901234567")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).isEqualTo("Địa chỉ cửa hàng đã được sử dụng");
        assertThat(shop.getSlug()).isEqualTo("cua-hang-a");
        assertThat(shop.getContactPhone()).isNull();
        verify(shopRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateStorefront_keepOwnSlug_doesNotConflict() {
        Shop shop = shop();
        when(shopRepository.findById(TENANT_ID)).thenReturn(Optional.of(shop));
        when(shopRepository.existsBySlugAndIdNot("cua-hang-a", TENANT_ID)).thenReturn(false);
        when(shopRepository.saveAndFlush(shop)).thenReturn(shop);

        ShopResponse response = shopService.updateStorefront(request(true, "cua-hang-a", "0901234567"));

        assertThat(response.getSlug()).isEqualTo("cua-hang-a");
        assertThat(response.isStorefrontEnabled()).isTrue();
        assertThat(response.getContactPhone()).isEqualTo("0901234567");
    }

    @Test
    void updateStorefront_emptyOrNullPhone_clearsPhone() {
        for (String phone : new String[]{"", null}) {
            Shop shop = shop();
            shop.setContactPhone("0901234567");
            when(shopRepository.findById(TENANT_ID)).thenReturn(Optional.of(shop));
            when(shopRepository.saveAndFlush(shop)).thenReturn(shop);

            ShopResponse response = shopService.updateStorefront(request(false, "cua-hang-a", phone));

            assertThat(response.getContactPhone()).isNull();
            assertThat(shop.getContactPhone()).isNull();
        }
    }

    /** Hai request cùng lọt qua bước check trùng: exception đi lên để handler chung trả 409. */
    @Test
    void updateStorefront_uniqueConstraintRace_propagatesDataIntegrityViolation() {
        Shop shop = shop();
        when(shopRepository.findById(TENANT_ID)).thenReturn(Optional.of(shop));
        when(shopRepository.saveAndFlush(shop)).thenThrow(new DataIntegrityViolationException("uk_slug"));

        assertThrows(DataIntegrityViolationException.class,
                () -> shopService.updateStorefront(request(true, "cua-hang-moi", null)));
    }

    private static Shop shop() {
        return Shop.builder().id(TENANT_ID).name("Cửa hàng A").slug("cua-hang-a").platform("MANUAL").build();
    }

    private static UpdateStorefrontRequest request(boolean enabled, String slug, String phone) {
        UpdateStorefrontRequest request = new UpdateStorefrontRequest();
        request.setStorefrontEnabled(enabled);
        request.setSlug(slug);
        request.setContactPhone(phone);
        return request;
    }
}

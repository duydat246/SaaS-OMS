package com.saasoms.modules.shop.controller;

import com.saasoms.common.exception.BusinessException;
import com.saasoms.common.exception.GlobalExceptionHandler;
import com.saasoms.modules.shop.dto.ShopResponse;
import com.saasoms.modules.shop.service.ShopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ShopControllerTest {

    @Mock
    private ShopService shopService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ShopController(shopService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void get_returnsShopShapeWithNullPhone() throws Exception {
        when(shopService.getCurrentShop()).thenReturn(response(false, null));

        mockMvc.perform(get("/api/shop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Cửa hàng A"))
                .andExpect(jsonPath("$.data.slug").value("cua-hang-a"))
                .andExpect(jsonPath("$.data.storefrontEnabled").value(false))
                .andExpect(jsonPath("$.data.contactPhone").value(nullValue()))
                .andExpect(jsonPath("$.data.tenantId").doesNotExist())
                .andExpect(jsonPath("$.data.id").doesNotExist());
    }

    @Test
    void updateStorefront_valid_returns200WithMessage() throws Exception {
        when(shopService.updateStorefront(any())).thenReturn(response(true, "0901234567"));

        mockMvc.perform(put("/api/shop/storefront")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storefrontEnabled":true,"slug":"cua-hang-a","contactPhone":"0901234567"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã lưu cài đặt trang bán hàng"))
                .andExpect(jsonPath("$.data.storefrontEnabled").value(true))
                .andExpect(jsonPath("$.data.contactPhone").value("0901234567"));
    }

    @Test
    void updateStorefront_invalidSlug_returns400Vietnamese() throws Exception {
        mockMvc.perform(put("/api/shop/storefront")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storefrontEnabled":true,"slug":"-1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Địa chỉ cửa hàng")));

        verifyNoInteractions(shopService);
    }

    @Test
    void updateStorefront_missingEnabled_returns400() throws Exception {
        mockMvc.perform(put("/api/shop/storefront")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"cua-hang-a"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("bật/tắt")));
    }

    @Test
    void updateStorefront_invalidPhone_returns400() throws Exception {
        mockMvc.perform(put("/api/shop/storefront")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storefrontEnabled":true,"slug":"cua-hang-a","contactPhone":"abc"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Số điện thoại")));
    }

    @Test
    void updateStorefront_malformedBody_returns400() throws Exception {
        mockMvc.perform(put("/api/shop/storefront")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storefrontEnabled\":\"khong-phai-boolean\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void updateStorefront_duplicateSlug_returns409() throws Exception {
        when(shopService.updateStorefront(any()))
                .thenThrow(BusinessException.conflict("Địa chỉ cửa hàng đã được sử dụng"));

        mockMvc.perform(put("/api/shop/storefront")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storefrontEnabled":true,"slug":"da-co"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Địa chỉ cửa hàng đã được sử dụng"));
    }

    @Test
    void updateStorefront_concurrentConstraintViolation_returns409() throws Exception {
        when(shopService.updateStorefront(any())).thenThrow(new DataIntegrityViolationException("uk_slug"));

        mockMvc.perform(put("/api/shop/storefront")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storefrontEnabled":true,"slug":"dua-cung-luc"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    private static ShopResponse response(boolean enabled, String phone) {
        return ShopResponse.builder()
                .name("Cửa hàng A")
                .slug("cua-hang-a")
                .storefrontEnabled(enabled)
                .contactPhone(phone)
                .build();
    }
}

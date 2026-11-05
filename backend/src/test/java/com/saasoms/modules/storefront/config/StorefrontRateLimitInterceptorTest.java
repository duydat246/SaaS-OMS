package com.saasoms.modules.storefront.config;

import com.saasoms.common.exception.BusinessException;
import com.saasoms.common.exception.GlobalExceptionHandler;
import com.saasoms.common.ratelimit.PublicRateLimiter;
import com.saasoms.modules.storefront.controller.StorefrontController;
import com.saasoms.modules.storefront.dto.StorefrontShopResponse;
import com.saasoms.modules.storefront.service.StorefrontService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StorefrontRateLimitInterceptorTest {

    private static final String BASE = "/api/public/shops/cua-hang-a";
    private static final String IP = "203.0.113.1";
    private static final String MESSAGE = "Bạn truy cập quá nhanh, vui lòng thử lại sau ít phút";

    @Mock
    private PublicRateLimiter publicRateLimiter;
    @Mock
    private StorefrontService storefrontService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StorefrontController(storefrontService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addMappedInterceptors(new String[]{"/api/public/**"},
                        new StorefrontRateLimitInterceptor(publicRateLimiter))
                .build();
    }

    @Test
    void api_overLimit_returns429ApiResponseVietnameseWithoutCallingService() throws Exception {
        doThrow(tooManyRequests()).when(publicRateLimiter).checkApi(IP);

        mockMvc.perform(get(BASE).header("X-Real-IP", IP))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(MESSAGE))
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(storefrontService);
    }

    @Test
    void image_overLimit_acceptImage_returns429Json() throws Exception {
        doThrow(tooManyRequests()).when(publicRateLimiter).checkImage(IP);

        mockMvc.perform(get(BASE + "/images/55").header("X-Real-IP", IP).accept("image/webp"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(MESSAGE));

        verifyNoInteractions(storefrontService);
    }

    @Test
    void imageRoute_countedInImageGroup() throws Exception {
        when(storefrontService.getImage("cua-hang-a", 55L)).thenReturn(
                new StorefrontService.StorefrontImage(new ByteArrayResource(new byte[]{1}), "image/jpeg"));

        mockMvc.perform(get(BASE + "/images/55").header("X-Real-IP", IP)).andExpect(status().isOk());

        verify(publicRateLimiter).checkImage(IP);
        verify(publicRateLimiter, never()).checkApi(IP);
    }

    @Test
    void apiRoute_countedInApiGroup() throws Exception {
        when(storefrontService.getShop("cua-hang-a")).thenReturn(StorefrontShopResponse.builder().build());

        mockMvc.perform(get(BASE).header("X-Real-IP", IP)).andExpect(status().isOk());

        verify(publicRateLimiter).checkApi(IP);
        verify(publicRateLimiter, never()).checkImage(IP);
    }

    /** Header không phải IP thì dùng địa chỉ kết nối, không được tạo khoá từ chuỗi client gửi. */
    @Test
    void invalidRealIpHeader_fallsBackToRemoteAddress() throws Exception {
        when(storefrontService.getShop("cua-hang-a")).thenReturn(StorefrontShopResponse.builder().build());

        mockMvc.perform(get(BASE).header("X-Real-IP", "khong-phai-ip")).andExpect(status().isOk());

        verify(publicRateLimiter).checkApi("127.0.0.1");
    }

    private static BusinessException tooManyRequests() {
        return new BusinessException(MESSAGE, HttpStatus.TOO_MANY_REQUESTS);
    }
}

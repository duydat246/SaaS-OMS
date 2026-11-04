package com.saasoms.modules.auth.controller;

import com.saasoms.common.exception.BusinessException;
import com.saasoms.common.exception.GlobalExceptionHandler;
import com.saasoms.modules.auth.dto.AuthResponse;
import com.saasoms.modules.auth.dto.LoginRequest;
import com.saasoms.modules.auth.dto.RegisterRequest;
import com.saasoms.modules.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String LOGIN_BODY = """
            {"email":"a@example.com","password":"sai"}
            """;

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void login_rateLimited_returns429WithVietnameseMessage() throws Exception {
        String message = "Bạn đã thử quá nhiều lần. Vui lòng thử lại sau 15 phút.";
        when(authService.login(any(LoginRequest.class), any()))
                .thenThrow(new BusinessException(message, HttpStatus.TOO_MANY_REQUESTS));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    void login_passesResolvedClientIpToService() throws Exception {
        when(authService.login(any(LoginRequest.class), any())).thenReturn(AuthResponse.builder().build());

        mockMvc.perform(post("/auth/login")
                        .header("X-Real-IP", "5.5.5.5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk());

        verify(authService).login(any(LoginRequest.class), eq("5.5.5.5"));
    }

    @Test
    void register_passesResolvedClientIpToService() throws Exception {
        when(authService.register(any(RegisterRequest.class), any())).thenReturn(AuthResponse.builder().build());

        mockMvc.perform(post("/auth/register")
                        .header("X-Real-IP", "5.5.5.5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shopName":"Shop","email":"a@example.com","password":"12345678","fullName":"A"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Đăng ký cửa hàng thành công"));

        verify(authService).register(any(RegisterRequest.class), eq("5.5.5.5"));
    }

    @Test
    void register_invalidBody_returns400WithoutCountingRateLimit() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shopName":"Shop","email":"a@example.com","password":"1234567","fullName":"A"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mật khẩu phải có từ 8 đến 72 ký tự"));

        verifyNoInteractions(authService);
    }

    @Test
    void refresh_emptyBody_returns400Vietnamese() throws Exception {
        mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Refresh token không được để trống"));
    }
}

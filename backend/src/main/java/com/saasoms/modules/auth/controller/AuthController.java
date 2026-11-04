package com.saasoms.modules.auth.controller;

import com.saasoms.common.response.ApiResponse;
import com.saasoms.common.util.ClientIpResolver;
import com.saasoms.modules.auth.dto.AuthResponse;
import com.saasoms.modules.auth.dto.LoginRequest;
import com.saasoms.modules.auth.dto.RefreshRequest;
import com.saasoms.modules.auth.dto.RegisterRequest;
import com.saasoms.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Đăng ký, đăng nhập và quản lý token")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Đăng ký cửa hàng và tài khoản chủ cửa hàng")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req,
                                              HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                authService.register(req, ClientIpResolver.resolve(httpRequest)),
                "Đăng ký cửa hàng thành công");
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập và nhận access token")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req,
                                           HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(req, ClientIpResolver.resolve(httpRequest)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Làm mới access token")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ApiResponse.ok(authService.refresh(req));
    }

    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất và thu hồi refresh token")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.getRefreshToken());
        return ApiResponse.ok(null, "Đã đăng xuất");
    }
}

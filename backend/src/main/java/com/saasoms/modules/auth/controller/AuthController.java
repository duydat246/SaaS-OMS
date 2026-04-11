package com.saasoms.modules.auth.controller;

import com.saasoms.common.response.ApiResponse;
import com.saasoms.modules.auth.dto.AuthResponse;
import com.saasoms.modules.auth.dto.LoginRequest;
import com.saasoms.modules.auth.dto.RefreshRequest;
import com.saasoms.modules.auth.dto.RegisterRequest;
import com.saasoms.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Register, login, token management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new shop and owner account")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.ok(authService.register(req), "Shop registered successfully");
    }

    @PostMapping("/login")
    @Operation(summary = "Login and get access token")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ApiResponse.ok(authService.refresh(req));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and revoke refresh token")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.getRefreshToken());
        return ApiResponse.ok(null, "Logged out successfully");
    }
}

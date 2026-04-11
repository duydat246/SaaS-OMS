package com.saasoms.modules.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private UserInfo user;

    @Data
    @Builder
    public static class UserInfo {
        private Long id;
        private String email;
        private String fullName;
        private String role;
        private Long tenantId;
        private String shopName;
    }
}

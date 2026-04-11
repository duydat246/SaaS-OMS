package com.saasoms.common.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class JwtDetails {
    private final Long userId;
    private final Long tenantId;
    private final String role;
}

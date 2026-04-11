package com.saasoms.common.util;

import com.saasoms.common.config.JwtDetails;
import com.saasoms.common.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    private SecurityUtils() {}

    public static Long getCurrentUserId() {
        return getDetails().getUserId();
    }

    public static Long getCurrentTenantId() {
        return TenantContext.get();
    }

    public static String getCurrentRole() {
        return getDetails().getRole();
    }

    public static boolean isOwner() {
        return "OWNER".equals(getCurrentRole());
    }

    private static JwtDetails getDetails() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (JwtDetails) auth.getDetails();
    }
}

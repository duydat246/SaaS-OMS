package com.saasoms.common.tenant;

public class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Long get() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}

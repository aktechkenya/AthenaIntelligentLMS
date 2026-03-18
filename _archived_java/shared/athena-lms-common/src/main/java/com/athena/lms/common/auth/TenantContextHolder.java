package com.athena.lms.common.auth;

/**
 * ThreadLocal holder for the current tenant ID, populated by LmsJwtAuthenticationFilter.
 * Always clear in a finally block after request processing.
 */
public final class TenantContextHolder {

    private static final ThreadLocal<String> TENANT = new InheritableThreadLocal<>();

    private TenantContextHolder() {}

    public static void setTenantId(String tenantId) {
        TENANT.set(tenantId);
    }

    public static String getTenantId() {
        return TENANT.get();
    }

    /** Returns tenantId or "default" if none set (single-tenant compatibility). */
    public static String getTenantIdOrDefault() {
        String tid = TENANT.get();
        return tid != null ? tid : "default";
    }

    public static void clear() {
        TENANT.remove();
    }
}

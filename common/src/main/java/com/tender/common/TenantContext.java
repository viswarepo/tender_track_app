package com.tender.common;

import java.util.UUID;

public final class TenantContext {
    private static final ThreadLocal<UUID> T = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID id) {
        T.set(id);
    }

    public static UUID require() {
        UUID v = T.get();
        if (v == null) throw new IllegalStateException("No tenant in context");
        return v;
    }

    public static void clear() {
        T.remove();
    }
}

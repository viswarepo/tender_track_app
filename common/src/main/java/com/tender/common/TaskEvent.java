package com.tender.common;

import java.time.Instant;
import java.util.UUID;

public record TaskEvent(UUID tenantId, String actor, String action, UUID taskId, Instant at) {
    public static final String TOPIC = "tender.events";
}

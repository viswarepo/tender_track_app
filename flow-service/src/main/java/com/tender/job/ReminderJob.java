package com.tender.job;

import com.tender.common.TenantContext;
import com.tender.service.ReminderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Hourly: create in-app reminders. Every morning at 08:00 (server time): do the same, then send one short digest
 * email event for each organization that still has unread reminders. Add ShedLock before running several instances.
 */
@Component
public class ReminderJob {
    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);
    private final ReminderService svc;

    public ReminderJob(ReminderService svc) {
        this.svc = svc;
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void hourly() {
        run(false);
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void morningDigest() {
        run(true);
    }

    private void run(boolean digest) {
        for (UUID tenant : svc.tenantsWithWork()) {
            TenantContext.set(tenant);
            try {
                int created = svc.generate();
                if (created > 0) log.info("Created {} reminder(s) for tenant {}", created, tenant);
                if (digest && svc.unreadCount() > 0) svc.emitDigest(tenant);
            } catch (Exception e) {
                log.error("Reminder job failed for tenant {}", tenant, e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}

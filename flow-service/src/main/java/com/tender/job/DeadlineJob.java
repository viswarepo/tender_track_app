package com.tender.job;

import com.tender.common.TenantContext;
import com.tender.service.FlowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Hourly: open cases due within 3 days get one alert event each. Add ShedLock before running several instances.
 */
@Component
public class DeadlineJob {
    private static final Logger log = LoggerFactory.getLogger(DeadlineJob.class);
    private final FlowService svc;

    public DeadlineJob(FlowService svc) {
        this.svc = svc;
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 45_000)
    public void run() {
        for (UUID tenant : svc.dueTenants()) {
            TenantContext.set(tenant);
            try {
                log.info("Alerted {} case(s) for tenant {}", svc.alertDue(), tenant);
            } catch (Exception e) {
                log.error("Deadline job failed for tenant {}", tenant, e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}

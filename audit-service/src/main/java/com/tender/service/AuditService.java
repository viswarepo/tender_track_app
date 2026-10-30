package com.tender.service;

import com.tender.common.*;
import com.tender.common.TaskEvent;
import com.tender.common.TenantContext;
import com.tender.common.TenantScope;
import com.tender.domain.AuditEntry;
import com.tender.repository.AuditRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditService {
    private final AuditRepository repo;
    private final TenantScope scope;

    public AuditService(AuditRepository repo, TenantScope scope) {
        this.repo = repo;
        this.scope = scope;
    }

    /**
     * Events carry their own tenant, so the consumer sets the tenant context itself.
     */
    @KafkaListener(topics = TaskEvent.TOPIC, groupId = "audit-service")
    @Transactional
    public void on(TaskEvent e) {
        TenantContext.set(e.tenantId());
        try {
            scope.apply();
            repo.save(new AuditEntry(e));
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> recent() {
        scope.apply();
        return repo.findAllByOrderByAtDesc();
    }
}

package com.tender.domain;

import com.tender.common.TaskEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditEntry {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    private String actor;
    private String action;
    @Column(name = "task_id")
    private UUID taskId;
    @Column(name = "at")
    private Instant at;

    protected AuditEntry() {
    }

    public AuditEntry(TaskEvent e) {
        tenantId = e.tenantId();
        actor = e.actor();
        action = e.action();
        taskId = e.taskId();
        at = e.at();
    }

    public UUID getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public Instant getAt() {
        return at;
    }
}

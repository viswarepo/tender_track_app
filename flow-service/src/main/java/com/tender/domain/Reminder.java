package com.tender.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** An in-app reminder: a deadline, deposit refund or expiry that is getting close. Shared by the whole organization. */
@Entity
@Table(name = "flow_reminders")
public class Reminder {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "ref_type",columnDefinition = "TEXT")
    private String refType;
    @Column(name = "ref_id")
    private UUID refId;
    @Column(name = "case_id")
    private UUID caseId;

    @Column(columnDefinition = "TEXT")
    private String kind;
    private int threshold;
    @Column(name = "due_on")
    private LocalDate dueOn;

    @Column(columnDefinition = "TEXT")
    private String level;

    @Column(columnDefinition = "TEXT")
    private String message;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    @Column(name = "read_at")
    private Instant readAt;

    protected Reminder() {
    }

    public Reminder(UUID tenantId, String refType, UUID refId, UUID caseId, String kind, int threshold, LocalDate dueOn, String level, String message) {
        this.tenantId = tenantId;
        this.refType = refType;
        this.refId = refId;
        this.caseId = caseId;
        this.kind = kind;
        this.threshold = threshold;
        this.dueOn = dueOn;
        this.level = level;
        this.message = message;
    }

    public void markRead() {
        if (readAt == null) readAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getRefType() { return refType; }
    public UUID getRefId() { return refId; }
    public UUID getCaseId() { return caseId; }
    public String getKind() { return kind; }
    public LocalDate getDueOn() { return dueOn; }
    public String getLevel() { return level; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}

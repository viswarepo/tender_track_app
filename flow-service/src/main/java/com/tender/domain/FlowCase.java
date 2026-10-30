package com.tender.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "flow_cases")
public class FlowCase {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "type_id")
    private UUID typeId;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String status;

    @Column(columnDefinition = "TEXT")
    private String data;
    private LocalDate deadline;
    @Column(name = "is_open")
    private boolean open = true;
    private boolean alerted;
    @Column(name = "created_by")
    private String createdBy;
    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    protected FlowCase() {
    }

    public FlowCase(UUID tenantId, UUID typeId, String title, String status, String data, LocalDate deadline, String createdBy) {
        this.tenantId = tenantId;
        this.typeId = typeId;
        this.title = title;
        this.status = status;
        this.data = data;
        this.deadline = deadline;
        this.createdBy = createdBy;
    }

    public void moveTo(String s, boolean stillOpen) {
        this.status = s;
        this.open = stillOpen;
        this.updatedAt = Instant.now();
    }

    public void update(String data, LocalDate newDeadline, String title) {
        if (!java.util.Objects.equals(this.deadline, newDeadline)) this.alerted = false; // new deadline, new reminder
        this.data = data;
        this.deadline = newDeadline;
        this.title = title;
        this.updatedAt = Instant.now();
    }

    public void markAlerted() {
        this.alerted = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getTypeId() {
        return typeId;
    }

    public String getTitle() {
        return title;
    }

    public String getStatus() {
        return status;
    }

    public String getData() {
        return data;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public boolean isOpen() {
        return open;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

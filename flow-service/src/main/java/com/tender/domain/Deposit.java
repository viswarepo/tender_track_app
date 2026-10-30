package com.tender.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A deposit, fee or guarantee tied to a tender: money that is locked up and should come back. */
@Entity
@Table(name = "flow_deposits")
public class Deposit {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "case_id")
    private UUID caseId;

    @Column(columnDefinition = "TEXT")
    private String kind;
    private BigDecimal amount;

    @Column(columnDefinition = "TEXT")
    private String currency;
    @Column(columnDefinition = "TEXT")
    private String instrument;
    @Column(columnDefinition = "TEXT")
    private String reference;

    @Column(columnDefinition = "TEXT")
    private String status = "PLANNED";
    @Column(name = "paid_on")
    private LocalDate paidOn;
    @Column(name = "valid_until")
    private LocalDate validUntil;
    @Column(name = "refund_due_on")
    private LocalDate refundDueOn;
    @Column(name = "refunded_on")
    private LocalDate refundedOn;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @Column(name = "created_by",columnDefinition = "TEXT")
    private String createdBy;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    protected Deposit() {
    }

    public Deposit(UUID tenantId, UUID caseId, String kind, BigDecimal amount, String currency, String instrument,
                   String reference, LocalDate validUntil, LocalDate refundDueOn, String notes, String createdBy) {
        this.tenantId = tenantId;
        this.caseId = caseId;
        this.kind = kind;
        this.amount = amount;
        this.currency = currency;
        this.instrument = instrument;
        this.reference = reference;
        this.validUntil = validUntil;
        this.refundDueOn = refundDueOn;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void markPaid(LocalDate on) {
        status = "PAID";
        paidOn = on;
    }

    public void markRefunded(LocalDate on) {
        status = "REFUNDED";
        refundedOn = on;
    }

    public void forfeit() {
        status = "FORFEITED";
    }

    public void setKind(String kind) { this.kind = kind; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setInstrument(String instrument) { this.instrument = instrument; }
    public void setReference(String reference) { this.reference = reference; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setPaidOn(LocalDate paidOn) { this.paidOn = paidOn; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public void setRefundDueOn(LocalDate refundDueOn) { this.refundDueOn = refundDueOn; }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCaseId() { return caseId; }
    public String getKind() { return kind; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getInstrument() { return instrument; }
    public String getReference() { return reference; }
    public String getStatus() { return status; }
    public LocalDate getPaidOn() { return paidOn; }
    public LocalDate getValidUntil() { return validUntil; }
    public LocalDate getRefundDueOn() { return refundDueOn; }
    public LocalDate getRefundedOn() { return refundedOn; }
    public String getNotes() { return notes; }
    public String getCreatedBy() { return createdBy; }
}

package com.tender.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "notification_settings")
public class Settings {
    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;
    @Column(name = "escalation_email")
    private String escalationEmail;

    protected Settings() {
    }

    public Settings(UUID tenantId, String escalationEmail) {
        this.tenantId = tenantId;
        this.escalationEmail = escalationEmail;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEscalationEmail() {
        return escalationEmail;
    }
}

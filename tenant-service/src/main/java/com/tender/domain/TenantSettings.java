package com.tender.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "tenant_settings")
public class TenantSettings {
    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;
    @Column(name = "display_name")
    private String displayName;
    @Column(name = "time_zone")
    private String timeZone;
    private String queues;
    private String sites;

    protected TenantSettings() {
    }

    public TenantSettings(UUID tenantId, String displayName, String timeZone, String queues, String sites) {
        this.tenantId = tenantId;
        this.displayName = displayName;
        this.timeZone = timeZone;
        this.queues = queues;
        this.sites = sites;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public String getQueues() {
        return queues;
    }

    public String getSites() {
        return sites;
    }
}

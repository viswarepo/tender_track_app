package com.tender.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A tenant's case type. The definition (fields, states, transitions, deadline field) is JSON, so new industries need data, not code.
 */
@Entity
@Table(name = "flow_case_types")
public class CaseType {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "type_key",columnDefinition = "TEXT")
    private String typeKey;

    @Column(columnDefinition = "TEXT")
    private String name;

    @Column(columnDefinition = "TEXT")
    private String definition;

    protected CaseType() {
    }

    public CaseType(UUID tenantId, String typeKey, String name, String definition) {
        this.tenantId = tenantId;
        this.typeKey = typeKey;
        this.name = name;
        this.definition = definition;
    }

    public void replaceDefinition(String name, String definition) {
        this.name = name;
        this.definition = definition;
    }

    public UUID getId() {
        return id;
    }

    public String getTypeKey() {
        return typeKey;
    }

    public String getName() {
        return name;
    }

    public String getDefinition() {
        return definition;
    }
}

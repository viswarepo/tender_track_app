package com.tender.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organizations")
public class Organization {
    @Id
    private UUID id;
    private String name;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    private boolean active = true;

    protected Organization() {
    }

    public Organization(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isActive() { return active; }
}

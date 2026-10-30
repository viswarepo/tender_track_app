package com.tender.identity.domain;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_accounts")
public class UserAccount {
    public enum Role { ADMIN, COORDINATOR, VIEWER }

    @Id
    private UUID id;
    @Column(name = "org_id")
    private UUID orgId;
    private String username;
    private String email;
    @Column(name = "display_name")
    private String displayName;
    @Column(name = "password_hash")
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    private Role role;
    private boolean enabled = true;
    @Column(name = "must_change_password")
    private boolean mustChangePassword;
    @Column(name = "failed_attempts")
    private int failedAttempts;
    @Column(name = "locked_until")
    private Instant lockedUntil;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected UserAccount() {
    }

    public UserAccount(UUID orgId, String username, String email, String displayName, String passwordHash, Role role, boolean mustChangePassword) {
        this.id = UUID.randomUUID();
        this.orgId = orgId;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.mustChangePassword = mustChangePassword;
    }

    public boolean isLocked() { return lockedUntil != null && lockedUntil.isAfter(Instant.now()); }

    public void registerFailure(int maxAttempts, Duration lockFor) {
        failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            lockedUntil = Instant.now().plus(lockFor);
            failedAttempts = 0;
        }
    }

    public void registerSuccess() {
        failedAttempts = 0;
        lockedUntil = null;
        lastLoginAt = Instant.now();
    }

    public void setPassword(String hash, boolean mustChange) {
        this.passwordHash = hash;
        this.mustChangePassword = mustChange;
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    public void updateProfile(String email, String displayName) {
        if (email != null) this.email = email;
        if (displayName != null) this.displayName = displayName;
    }

    public void setRole(Role role) { this.role = role; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public UUID getId() { return id; }
    public UUID getOrgId() { return orgId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
}

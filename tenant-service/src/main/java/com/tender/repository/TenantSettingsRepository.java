package com.tender.repository;

import com.tender.domain.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TenantSettingsRepository extends JpaRepository<TenantSettings, UUID> {
}

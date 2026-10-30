package com.tender.repository;

import java.util.UUID;

import com.tender.domain.Settings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingsRepository extends JpaRepository<Settings, UUID> {
}

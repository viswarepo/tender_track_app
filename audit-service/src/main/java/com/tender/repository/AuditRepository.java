package com.tender.repository;

import com.tender.domain.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditRepository extends JpaRepository<AuditEntry, UUID> {
    List<AuditEntry> findAllByOrderByAtDesc();
}

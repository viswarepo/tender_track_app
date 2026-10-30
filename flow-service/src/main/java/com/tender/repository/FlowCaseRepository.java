package com.tender.repository;

import com.tender.domain.FlowCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FlowCaseRepository extends JpaRepository<FlowCase, UUID> {
    List<FlowCase> findAllByOrderByUpdatedAtDesc();

    List<FlowCase> findByOpenTrue();

    List<FlowCase> findByOpenTrueAndAlertedFalseAndDeadlineLessThanEqual(LocalDate d);

    /**
     * Security-definer function returning only tenant ids, so the job never bypasses row-level security.
     */
    @Query(value = "select tenants_with_due_cases()", nativeQuery = true)
    List<UUID> dueTenants();
}

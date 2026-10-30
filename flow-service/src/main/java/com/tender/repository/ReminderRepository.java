package com.tender.repository;

import com.tender.domain.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {
    List<Reminder> findTop100ByOrderByCreatedAtDesc();

    List<Reminder> findTop100ByReadAtIsNullOrderByCreatedAtDesc();

    boolean existsByRefIdAndKindAndThresholdAndDueOn(UUID refId, String kind, int threshold, LocalDate dueOn);

    long countByReadAtIsNull();

    /** Security-definer function returning only tenant ids, so the job never bypasses row-level security. */
    @Query(value = "select tenants_with_reminder_work()", nativeQuery = true)
    List<UUID> tenantsWithWork();
}

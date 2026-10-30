package com.tender.repository;

import com.tender.domain.Deposit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepositRepository extends JpaRepository<Deposit, UUID> {
    List<Deposit> findAllByOrderByCreatedAtDesc();

    List<Deposit> findByCaseIdOrderByCreatedAtDesc(UUID caseId);

    List<Deposit> findByStatus(String status);
}

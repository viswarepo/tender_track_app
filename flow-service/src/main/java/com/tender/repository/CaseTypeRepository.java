package com.tender.repository;

import com.tender.domain.CaseType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseTypeRepository extends JpaRepository<CaseType, UUID> {
    Optional<CaseType> findByTypeKey(String key);

    List<CaseType> findAllByOrderByNameAsc();
}

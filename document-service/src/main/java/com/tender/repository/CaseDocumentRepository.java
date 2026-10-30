package com.tender.repository;
import java.util.*;

import com.tender.domain.CaseDocument;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CaseDocumentRepository extends JpaRepository<CaseDocument, UUID> {
  List<CaseDocument> findByCaseIdOrderByUploadedAtDesc(UUID caseId);
}

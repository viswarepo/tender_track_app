package com.tender.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Metadata for one uploaded file. The file itself is stored on disk under a path built only from UUIDs. */
@Entity @Table(name = "case_documents")
public class CaseDocument {
  @Id private UUID id;
  @Column(name = "tenant_id", nullable = false, updatable = false) private UUID tenantId;
  @Column(name = "case_id") private UUID caseId;
  @Column(name = "checklist_item") private String checklistItem;
  @Column(name = "file_name") private String fileName;
  @Column(name = "size_bytes") private long sizeBytes;
  @Column(name = "storage_path") private String storagePath;
  @Column(name = "uploaded_by") private String uploadedBy;
  @Column(name = "uploaded_at") private Instant uploadedAt = Instant.now();

  protected CaseDocument() {}
  public CaseDocument(UUID id, UUID tenantId, UUID caseId, String checklistItem, String fileName, long sizeBytes, String storagePath, String uploadedBy) {
    this.id = id; this.tenantId = tenantId; this.caseId = caseId; this.checklistItem = checklistItem;
    this.fileName = fileName; this.sizeBytes = sizeBytes; this.storagePath = storagePath; this.uploadedBy = uploadedBy;
  }
  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getCaseId() { return caseId; }
  public String getChecklistItem() { return checklistItem; }
  public String getFileName() { return fileName; }
  public long getSizeBytes() { return sizeBytes; }
  public String getStoragePath() { return storagePath; }
  public String getUploadedBy() { return uploadedBy; }
  public Instant getUploadedAt() { return uploadedAt; }
}

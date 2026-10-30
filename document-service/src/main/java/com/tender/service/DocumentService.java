package com.tender.service;
import com.tender.common.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

import com.tender.domain.CaseDocument;
import com.tender.repository.CaseDocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentService {
  static final long MAX_BYTES = 20L * 1024 * 1024;
  static final Set<String> ALLOWED = new TreeSet<>(Set.of("pdf", "doc", "docx", "xls", "xlsx", "png", "jpg", "jpeg", "txt", "csv"));

  public record View(UUID id, UUID caseId, String checklistItem, String fileName, long sizeBytes, String uploadedBy, Instant uploadedAt) {}
  public record Download(String fileName, Resource body) {}

  private final CaseDocumentRepository repo; private final TenantScope scope; private final KafkaTemplate<String, TaskEvent> kafka;
  private final Path root;
  public DocumentService(CaseDocumentRepository repo, TenantScope scope, KafkaTemplate<String, TaskEvent> kafka,
                         @Value("${app.storage.root:./data/documents}") String rootDir) {
    this.repo = repo; this.scope = scope; this.kafka = kafka; this.root = Path.of(rootDir).toAbsolutePath().normalize();
  }

  @Transactional(readOnly = true)
  public List<View> list(UUID caseId) {
    scope.apply();
    return repo.findByCaseIdOrderByUploadedAtDesc(caseId).stream().map(this::view).toList();
  }

  @Transactional
  public View upload(UUID caseId, String checklistItem, MultipartFile file, String actor) {
    scope.apply();
    if (file == null || file.isEmpty()) throw bad("Choose a file first");
    if (file.getSize() > MAX_BYTES) throw bad("The file is larger than 20 MB");
    String name = cleanName(file.getOriginalFilename());
    String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
    if (!ALLOWED.contains(ext)) throw bad("This file type is not allowed. Allowed: " + String.join(", ", ALLOWED));

    UUID tenant = TenantContext.require(), id = UUID.randomUUID();
    Path dir = root.resolve(tenant.toString()), target = dir.resolve(id.toString());
    try {
      Files.createDirectories(dir);
      try (var in = file.getInputStream()) { Files.copy(in, target); }
    } catch (IOException e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the file"); }
    try {
      String item = checklistItem == null || checklistItem.isBlank() ? null : checklistItem.trim();
      var d = repo.save(new CaseDocument(id, tenant, caseId, item, name, file.getSize(), target.toString(), actor));
      emit(d, actor, "DOCUMENT_UPLOADED");
      return view(d);
    } catch (RuntimeException e) {
      try { Files.deleteIfExists(target); } catch (IOException ignored) { }
      throw e;
    }
  }

  @Transactional(readOnly = true)
  public Download download(UUID id) {
    scope.apply();
    var d = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); // another tenant's id looks like "not found"
    Path p = Path.of(d.getStoragePath()).toAbsolutePath().normalize();
    if (!p.startsWith(root) || !Files.isReadable(p)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The file is missing on the server");
    return new Download(d.getFileName(), new FileSystemResource(p));
  }

  @Transactional
  public void delete(UUID id, String actor) {
    scope.apply();
    var d = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    repo.delete(d);
    emit(d, actor, "DOCUMENT_DELETED");
    try { Files.deleteIfExists(Path.of(d.getStoragePath())); } catch (IOException ignored) { }
  }

  // ---- helpers ----
  static String cleanName(String original) {
    String n = original == null ? "file" : original.replace("\\", "/");
    n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_").trim();
    if (n.isEmpty() || n.startsWith(".")) n = "file" + n;
    return n.length() > 150 ? n.substring(n.length() - 150) : n;
  }
  private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
  private View view(CaseDocument d) { return new View(d.getId(), d.getCaseId(), d.getChecklistItem(), d.getFileName(), d.getSizeBytes(), d.getUploadedBy(), d.getUploadedAt()); }
  private void emit(CaseDocument d, String actor, String action) {
    kafka.send(TaskEvent.TOPIC, d.getTenantId().toString(), new TaskEvent(d.getTenantId(), actor, action, d.getCaseId(), Instant.now()));
  }
}

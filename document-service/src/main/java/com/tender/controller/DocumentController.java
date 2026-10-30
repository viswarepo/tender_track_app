package com.tender.controller;

import com.tender.service.DocumentService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService svc;

    public DocumentController(DocumentService svc) {
        this.svc = svc;
    }

    @GetMapping
    public List<DocumentService.View> list(@RequestParam UUID caseId) {
        return svc.list(caseId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR')")
    public DocumentService.View upload(@RequestParam UUID caseId, @RequestParam(required = false) String checklistItem,
                                       @RequestParam MultipartFile file, Authentication a) {
        return svc.upload(caseId, checklistItem, file, a.getName());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        var f = svc.download(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(f.body());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR')")
    public Map<String, Boolean> delete(@PathVariable UUID id, Authentication a) {
        svc.delete(id, a.getName());
        return Map.of("deleted", true);
    }
}

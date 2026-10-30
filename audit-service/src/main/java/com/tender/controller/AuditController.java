package com.tender.controller;

import com.tender.domain.AuditEntry;
import com.tender.service.AuditService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditService svc;

    public AuditController(AuditService svc) {
        this.svc = svc;
    }

    @GetMapping("/log")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditEntry> recent() {
        String welcome ="welcome";
        return svc.recent();
    }
}

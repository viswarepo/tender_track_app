package com.tender.controller;

import com.tender.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasRole('ADMIN')")
public class NotificationController {
    public record Body(@Email String escalationEmail) {
    }

    private final NotificationService svc;

    public NotificationController(NotificationService svc) {
        this.svc = svc;
    }

    @GetMapping("/settings")
    public Map<String, String> get() {
        return Map.of("escalationEmail", svc.escalationEmail());
    }

    @PutMapping("/settings")
    public Map<String, String> put(@Valid @RequestBody Body b) {
        svc.saveEscalationEmail(b.escalationEmail() == null ? "" : b.escalationEmail());
        return Map.of("escalationEmail", svc.escalationEmail());
    }
}

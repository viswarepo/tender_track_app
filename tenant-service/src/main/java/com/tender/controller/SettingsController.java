package com.tender.controller;

import com.tender.service.SettingsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenant/settings")
public class SettingsController {
    private final SettingsService svc;

    public SettingsController(SettingsService svc) {
        this.svc = svc;
    }

    @GetMapping
    public SettingsService.Cfg get() {

        return svc.get();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsService.Cfg put(@RequestBody SettingsService.Cfg c) {
        return svc.save(c);
    }
}

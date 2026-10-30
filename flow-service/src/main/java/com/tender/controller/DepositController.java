package com.tender.controller;

import com.tender.service.DepositService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/flow/deposits")
public class DepositController {
    private final DepositService svc;

    public DepositController(DepositService svc) {
        this.svc = svc;
    }

    @GetMapping
    public List<DepositService.View> list(@RequestParam(required = false) UUID caseId) {
        return svc.list(caseId);
    }

    @GetMapping("/summary")
    public DepositService.Summary summary() {
        return svc.summary();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR')")
    public DepositService.View create(@Valid @RequestBody DepositService.Create r, Authentication a) {
        return svc.create(r, a.getName());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR')")
    public DepositService.View update(@PathVariable UUID id, @RequestBody DepositService.Change p, Authentication a) {
        return svc.update(id, p, a.getName());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR')")
    public Map<String, Boolean> delete(@PathVariable UUID id, Authentication a) {
        svc.delete(id, a.getName());
        return Map.of("deleted", true);
    }
}

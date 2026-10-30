package com.tender.controller;

import com.tender.service.FlowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/flow")
public class FlowController {
    public record CreateReq(@NotBlank String typeKey, @NotNull Map<String, Object> data) {
    }

    public record TransitionReq(@NotBlank String to) {
    }

    private final FlowService svc;

    public FlowController(FlowService svc) {
        this.svc = svc;
    }

    @GetMapping("/types")
    public List<FlowService.TypeView> types() {
        return svc.listTypes();
    }

    @PostMapping("/templates/{key}/install")
    @PreAuthorize("hasRole('ADMIN')")
    public FlowService.TypeView install(@PathVariable String key) {
        return svc.install(key);
    }

    @PutMapping("/templates/{key}/upgrade")
    @PreAuthorize("hasRole('ADMIN')")
    public FlowService.TypeView upgrade(@PathVariable String key) {
        return svc.upgrade(key);
    }

    @GetMapping("/cases")
    public List<FlowService.View> list(@RequestParam(required = false) String type, Authentication a) {
        return svc.list(type, roles(a), a.getName());
    }

    @PostMapping("/cases")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR')")
    public FlowService.View create(@Valid @RequestBody CreateReq r, Authentication a) {
        return svc.create(r.typeKey(), r.data(), a.getName(), roles(a));
    }

    @PutMapping("/cases/{id}/data")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR')")
    public FlowService.View update(@PathVariable UUID id, @RequestBody Map<String, Object> patch, Authentication a) {
        return svc.updateData(id, patch, a.getName(), roles(a));
    }

    @PostMapping("/cases/{id}/transition")
    public FlowService.View transition(@PathVariable UUID id, @Valid @RequestBody TransitionReq r, Authentication a) {
        return svc.transition(id, r.to(), a.getName(), roles(a));
    }

    private Set<String> roles(Authentication a) {
        return a.getAuthorities().stream().map(g -> g.getAuthority().replace("ROLE_", "")).collect(Collectors.toSet());
    }
}

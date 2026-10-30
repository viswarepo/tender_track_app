package com.tender.service;

import com.tender.common.*;
import com.tender.common.TenantContext;
import com.tender.common.TenantScope;
import com.tender.domain.TenantSettings;
import com.tender.repository.TenantSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

@Service
public class SettingsService {
    public record Cfg(String displayName, String timeZone, List<String> queues, List<String> sites) {
    }

    static final Cfg DEFAULT = new Cfg("", "UTC", List.of("Prior auth", "Referral", "Results review", "Outreach"), List.of());
    private final TenantSettingsRepository repo;
    private final TenantScope scope;

    public SettingsService(TenantSettingsRepository repo, TenantScope scope) {
        this.repo = repo;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public Cfg get() {
        scope.apply();

        Cfg cfg = repo.findById(TenantContext.require()).map(s -> new Cfg(s.getDisplayName(), s.getTimeZone(), split(s.getQueues()), split(s.getSites()))).orElse(DEFAULT);
        return cfg;
    }

    @Transactional
    public Cfg save(Cfg c) {
        scope.apply();
        try {
            ZoneId.of(c.timeZone());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown time zone");
        }
        var queues = clean(c.queues());
        if (queues.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add at least one queue");
        repo.save(new TenantSettings(TenantContext.require(), c.displayName() == null ? "" : c.displayName().trim(), c.timeZone(), String.join("\n", queues), String.join("\n", clean(c.sites()))));
        return new Cfg(c.displayName() == null ? "" : c.displayName().trim(), c.timeZone(), queues, clean(c.sites()));
    }

    private static List<String> clean(List<String> in) {
        return in == null ? List.of() : in.stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }

    private static List<String> split(String s) {
        return s == null || s.isBlank() ? List.of() : Arrays.asList(s.split("\n"));
    }
}

package com.tender.controller;

import com.tender.service.ReminderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/flow/reminders")
public class ReminderController {
    private final ReminderService svc;

    public ReminderController(ReminderService svc) {
        this.svc = svc;
    }

    @GetMapping
    public List<ReminderService.View> list(@RequestParam(defaultValue = "false") boolean unread) {
        return svc.list(unread);
    }

    @GetMapping("/count")
    public Map<String, Long> count() {
        return Map.of("unread", svc.unreadCount());
    }

    /** Works out any reminders that are due right now (instead of waiting for the next scheduled run). */
    @PostMapping("/refresh")
    public Map<String, Integer> refresh() {
        return Map.of("created", svc.generate());
    }

    @PostMapping("/{id}/read")
    public Map<String, Boolean> read(@PathVariable UUID id) {
        svc.markRead(id);
        return Map.of("read", true);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> readAll() {
        return Map.of("read", svc.markAllRead());
    }
}

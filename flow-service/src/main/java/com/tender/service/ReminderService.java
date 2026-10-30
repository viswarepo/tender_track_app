package com.tender.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tender.common.TaskEvent;
import com.tender.common.TenantContext;
import com.tender.common.TenantScope;
import com.tender.domain.CaseType;
import com.tender.domain.Deposit;
import com.tender.domain.FlowCase;
import com.tender.domain.Reminder;
import com.tender.repository.CaseTypeRepository;
import com.tender.repository.DepositRepository;
import com.tender.repository.FlowCaseRepository;
import com.tender.repository.ReminderRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Builds reminders from three places: each case type's "reminders" setting (which date fields to watch, and how many days
 * ahead), paid deposits (refund due, guarantee expiry), and a default for older templates without that setting.
 * For every item it keeps only the tightest threshold reached, and records it so it is never repeated.
 */
@Service
public class ReminderService {
    public record View(UUID id, String refType, UUID caseId, String typeKey, String kind, String level, String message,
                       LocalDate dueOn, Instant createdAt, boolean read) {
    }

    private record Spec(String field, String label, List<Integer> days, Set<String> skip) {
    }

    private final ReminderRepository reminders;
    private final FlowCaseRepository cases;
    private final CaseTypeRepository types;
    private final DepositRepository deposits;
    private final TenantScope scope;
    private final KafkaTemplate<String, TaskEvent> kafka;
    private final ObjectMapper om;

    public ReminderService(ReminderRepository reminders, FlowCaseRepository cases, CaseTypeRepository types, DepositRepository deposits,
                           TenantScope scope, KafkaTemplate<String, TaskEvent> kafka, ObjectMapper om) {
        this.reminders = reminders;
        this.cases = cases;
        this.types = types;
        this.deposits = deposits;
        this.scope = scope;
        this.kafka = kafka;
        this.om = om;
    }

    @Transactional(readOnly = true)
    public List<View> list(boolean unreadOnly) {
        scope.apply();
        List<Reminder> rows = unreadOnly ? reminders.findTop100ByReadAtIsNullOrderByCreatedAtDesc() : reminders.findTop100ByOrderByCreatedAtDesc();
        Set<UUID> caseIds = new HashSet<>();
        rows.forEach(r -> {
            if (r.getCaseId() != null) caseIds.add(r.getCaseId());
        });
        Map<UUID, String> keyByType = new HashMap<>();
        types.findAll().forEach(t -> keyByType.put(t.getId(), t.getTypeKey()));
        Map<UUID, String> keyByCase = new HashMap<>();
        cases.findAllById(caseIds).forEach(c -> keyByCase.put(c.getId(), keyByType.get(c.getTypeId())));
        return rows.stream().map(r -> new View(r.getId(), r.getRefType(), r.getCaseId(), keyByCase.get(r.getCaseId()), r.getKind(),
                r.getLevel(), r.getMessage(), r.getDueOn(), r.getCreatedAt(), r.getReadAt() != null)).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        scope.apply();
        return reminders.countByReadAtIsNull();
    }

    @Transactional
    public void markRead(UUID id) {
        scope.apply();
        reminders.findById(id).ifPresent(Reminder::markRead);
    }

    @Transactional
    public int markAllRead() {
        scope.apply();
        List<Reminder> unread = reminders.findTop100ByReadAtIsNullOrderByCreatedAtDesc();
        unread.forEach(Reminder::markRead);
        return unread.size();
    }

    @Transactional(readOnly = true)
    public List<UUID> tenantsWithWork() {
        return reminders.tenantsWithWork();
    }

    /** Creates any reminders that are now due for the current tenant. Safe to run often: nothing is created twice. */
    @Transactional
    public int generate() {
        scope.apply();
        int created = 0;
        Map<UUID, CaseType> typeById = new HashMap<>();
        types.findAll().forEach(t -> typeById.put(t.getId(), t));

        for (FlowCase c : cases.findByOpenTrue()) {
            CaseType type = typeById.get(c.getTypeId());
            if (type == null) continue;
            JsonNode def = parse(type.getDefinition());
            JsonNode data = parse(c.getData());
            for (Spec spec : specs(def)) {
                if (spec.skip().contains(c.getStatus())) continue;
                created += remind("CASE", c.getId(), c.getId(), spec.field(), spec.days(), date(data.path(spec.field()).asText("")), spec.label(), c.getTitle(), null);
            }
        }
        for (Deposit d : deposits.findByStatus("PAID")) {
            String title = cases.findById(d.getCaseId()).map(FlowCase::getTitle).orElse("a tender");
            String money = d.getAmount().stripTrailingZeros().toPlainString() + " " + d.getCurrency();
            created += remind("DEPOSIT", d.getId(), d.getCaseId(), "refundDueOn", List.of(7, 1, 0), d.getRefundDueOn(), kindLabel(d.getKind()) + " refund due", title, money);
            created += remind("DEPOSIT", d.getId(), d.getCaseId(), "validUntil", List.of(30, 7, 0), d.getValidUntil(), kindLabel(d.getKind()) + " expires", title, money);
        }
        return created;
    }

    /** One short email-style event per organization; the notification service sends a generic "you have reminders" message. */
    public void emitDigest(UUID tenantId) {
        kafka.send(TaskEvent.TOPIC, tenantId.toString(), new TaskEvent(tenantId, "system", "REMINDERS_DIGEST", tenantId, Instant.now()));
    }

    // ---- helpers ----
    private int remind(String refType, UUID refId, UUID caseId, String kind, List<Integer> days, LocalDate due, String label, String title, String extra) {
        if (due == null) return 0;
        long left = ChronoUnit.DAYS.between(LocalDate.now(), due);
        int threshold;
        if (left < 0) {
            threshold = -1; // overdue: reminded once
        } else {
            Integer tightest = days.stream().filter(x -> left <= x).min(Integer::compare).orElse(null);
            if (tightest == null) return 0; // not close enough yet
            threshold = tightest;
        }
        if (reminders.existsByRefIdAndKindAndThresholdAndDueOn(refId, kind, threshold, due)) return 0;
        String when = left < 0 ? "passed " + (-left) + (left == -1 ? " day ago" : " days ago")
                : left == 0 ? "is today" : "in " + left + (left == 1 ? " day" : " days");
        String level = left < 0 ? "urgent" : left <= 3 ? "warn" : "info";
        String message = label + " " + when + ": " + title + (extra == null ? "" : " (" + extra + ")");
        try {
            reminders.save(new Reminder(TenantContext.require(), refType, refId, caseId, kind, threshold, due, level, message));
            return 1;
        } catch (DataIntegrityViolationException duplicate) {
            return 0; // another run created it first
        }
    }

    private List<Spec> specs(JsonNode def) {
        List<Spec> out = new ArrayList<>();
        JsonNode arr = def.path("reminders");
        if (arr.isArray()) {
            for (JsonNode s : arr) {
                String field = s.path("field").asText("");
                List<Integer> days = new ArrayList<>();
                s.path("days").forEach(d -> days.add(d.asInt()));
                Set<String> skip = new HashSet<>();
                s.path("skipStates").forEach(x -> skip.add(x.asText()));
                if (!field.isEmpty() && !days.isEmpty()) out.add(new Spec(field, s.path("label").asText(field), days, skip));
            }
        }
        if (out.isEmpty()) { // templates installed before reminders existed
            String field = def.path("deadlineField").asText("");
            if (!field.isEmpty()) out.add(new Spec(field, "Deadline", List.of(7, 3, 1, 0), Set.of("SUBMITTED")));
        }
        return out;
    }

    private static LocalDate date(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String kindLabel(String k) {
        return switch (k) {
            case "EARNEST_MONEY" -> "Earnest money";
            case "TENDER_FEE" -> "Tender fee";
            case "BID_GUARANTEE" -> "Bid guarantee";
            case "PERFORMANCE_GUARANTEE" -> "Performance guarantee";
            default -> "Deposit";
        };
    }

    private JsonNode parse(String s) {
        try {
            return om.readTree(s);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}

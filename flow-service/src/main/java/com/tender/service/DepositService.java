package com.tender.service;

import com.tender.common.TaskEvent;
import com.tender.common.TenantContext;
import com.tender.common.TenantScope;
import com.tender.domain.Deposit;
import com.tender.domain.FlowCase;
import com.tender.repository.DepositRepository;
import com.tender.repository.FlowCaseRepository;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class DepositService {
    static final Set<String> KINDS = Set.of("EARNEST_MONEY", "TENDER_FEE", "BID_GUARANTEE", "PERFORMANCE_GUARANTEE", "OTHER");
    static final Set<String> INSTRUMENTS = Set.of("BANK_GUARANTEE", "DEMAND_DRAFT", "ONLINE", "CHEQUE", "CASH", "OTHER");

    public record View(UUID id, UUID caseId, String caseTitle, String kind, BigDecimal amount, String currency, String instrument,
                       String reference, String status, LocalDate paidOn, LocalDate validUntil, LocalDate refundDueOn,
                       LocalDate refundedOn, String notes, String createdBy, boolean refundOverdue) {
    }

    public record Summary(Map<String, BigDecimal> lockedByCurrency, long paidCount, long refundsDueIn7Days, long refundsOverdue,
                          long guaranteesExpiringIn30Days) {
    }

    public record Create(@NotNull UUID caseId, @NotBlank String kind, @NotNull @DecimalMin("0.01") BigDecimal amount,
                         String currency, String instrument, String reference, LocalDate paidOn, LocalDate validUntil,
                         LocalDate refundDueOn, String notes) {
    }

    public record Change(String kind, BigDecimal amount, String currency, String instrument, String reference, String status,
                         LocalDate paidOn, LocalDate validUntil, LocalDate refundDueOn, LocalDate refundedOn, String notes) {
    }

    private final DepositRepository deposits;
    private final FlowCaseRepository cases;
    private final TenantScope scope;
    private final KafkaTemplate<String, TaskEvent> kafka;

    public DepositService(DepositRepository deposits, FlowCaseRepository cases, TenantScope scope, KafkaTemplate<String, TaskEvent> kafka) {
        this.deposits = deposits;
        this.cases = cases;
        this.scope = scope;
        this.kafka = kafka;
    }

    @Transactional(readOnly = true)
    public List<View> list(UUID caseId) {
        scope.apply();
        List<Deposit> rows = caseId == null ? deposits.findAllByOrderByCreatedAtDesc() : deposits.findByCaseIdOrderByCreatedAtDesc(caseId);
        Map<UUID, String> titles = titles(rows);
        return rows.stream().map(d -> view(d, titles)).toList();
    }

    /** Money tied up in paid deposits, and what is coming due. */
    @Transactional(readOnly = true)
    public Summary summary() {
        scope.apply();
        LocalDate today = LocalDate.now();
        Map<String, BigDecimal> locked = new TreeMap<>();
        long paid = 0, due7 = 0, overdue = 0, expiring = 0;
        for (Deposit d : deposits.findByStatus("PAID")) {
            paid++;
            locked.merge(d.getCurrency(), d.getAmount(), BigDecimal::add);
            if (d.getRefundDueOn() != null) {
                long left = ChronoUnit.DAYS.between(today, d.getRefundDueOn());
                if (left < 0) overdue++;
                else if (left <= 7) due7++;
            }
            if (d.getValidUntil() != null) {
                long left = ChronoUnit.DAYS.between(today, d.getValidUntil());
                if (left >= 0 && left <= 30) expiring++;
            }
        }
        return new Summary(locked, paid, due7, overdue, expiring);
    }

    @Transactional
    public View create(Create r, String actor) {
        scope.apply();
        FlowCase tender = cases.findById(r.caseId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such tender in your organization"));
        Deposit d = new Deposit(TenantContext.require(), tender.getId(), kind(r.kind()), r.amount(), currency(r.currency()),
                instrument(r.instrument()), clean(r.reference()), r.validUntil(), r.refundDueOn(), clean(r.notes()), actor);
        if (r.paidOn() != null) d.markPaid(r.paidOn()); // giving a paid date means it has been paid
        deposits.save(d);
        emit(d, actor, d.getStatus().equals("PAID") ? "DEPOSIT_PAID" : "DEPOSIT_ADDED");
        return view(d, Map.of(tender.getId(), tender.getTitle()));
    }

    @Transactional
    public View update(UUID id, Change p, String actor) {
        scope.apply();
        Deposit d = find(id);
        if (p.kind() != null) d.setKind(kind(p.kind()));
        if (p.amount() != null) {
            if (p.amount().signum() <= 0) throw bad("The amount must be more than zero");
            d.setAmount(p.amount());
        }
        if (p.currency() != null) d.setCurrency(currency(p.currency()));
        if (p.instrument() != null) d.setInstrument(instrument(p.instrument()));
        if (p.reference() != null) d.setReference(clean(p.reference()));
        if (p.notes() != null) d.setNotes(clean(p.notes()));
        if (p.validUntil() != null) d.setValidUntil(p.validUntil());
        if (p.refundDueOn() != null) d.setRefundDueOn(p.refundDueOn());
        if (p.paidOn() != null) d.setPaidOn(p.paidOn());
        String action = "DEPOSIT_UPDATED";
        if (p.status() != null && !p.status().equalsIgnoreCase(d.getStatus())) {
            action = changeStatus(d, p.status().toUpperCase(), p.paidOn(), p.refundedOn());
        }
        emit(d, actor, action);
        return view(d, titles(List.of(d)));
    }

    @Transactional
    public void delete(UUID id, String actor) {
        scope.apply();
        Deposit d = find(id);
        deposits.delete(d);
        emit(d, actor, "DEPOSIT_DELETED");
    }

    // ---- helpers ----
    private String changeStatus(Deposit d, String to, LocalDate paidOn, LocalDate refundedOn) {
        switch (to) {
            case "PAID" -> {
                if (!"PLANNED".equals(d.getStatus())) throw conflict("Only a planned deposit can be marked paid");
                d.markPaid(paidOn != null ? paidOn : LocalDate.now());
                return "DEPOSIT_PAID";
            }
            case "REFUNDED" -> {
                if (!"PAID".equals(d.getStatus())) throw conflict("Only a paid deposit can be marked refunded");
                d.markRefunded(refundedOn != null ? refundedOn : LocalDate.now());
                return "DEPOSIT_REFUNDED";
            }
            case "FORFEITED" -> {
                if (!"PAID".equals(d.getStatus())) throw conflict("Only a paid deposit can be marked forfeited");
                d.forfeit();
                return "DEPOSIT_FORFEITED";
            }
            default -> throw bad("Status must be PAID, REFUNDED or FORFEITED");
        }
    }

    private Deposit find(UUID id) {
        return deposits.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such deposit in your organization"));
    }

    private Map<UUID, String> titles(List<Deposit> rows) {
        Set<UUID> ids = new HashSet<>();
        rows.forEach(d -> ids.add(d.getCaseId()));
        Map<UUID, String> titles = new HashMap<>();
        cases.findAllById(ids).forEach(c -> titles.put(c.getId(), c.getTitle()));
        return titles;
    }

    private View view(Deposit d, Map<UUID, String> titles) {
        boolean overdue = "PAID".equals(d.getStatus()) && d.getRefundDueOn() != null && d.getRefundDueOn().isBefore(LocalDate.now());
        return new View(d.getId(), d.getCaseId(), titles.get(d.getCaseId()), d.getKind(), d.getAmount(), d.getCurrency(), d.getInstrument(),
                d.getReference(), d.getStatus(), d.getPaidOn(), d.getValidUntil(), d.getRefundDueOn(), d.getRefundedOn(), d.getNotes(),
                d.getCreatedBy(), overdue);
    }

    private static String kind(String k) {
        String v = k == null ? "" : k.trim().toUpperCase();
        if (!KINDS.contains(v)) throw bad("Type must be one of " + new TreeSet<>(KINDS));
        return v;
    }

    private static String instrument(String i) {
        if (i == null || i.isBlank()) return null;
        String v = i.trim().toUpperCase();
        if (!INSTRUMENTS.contains(v)) throw bad("Instrument must be one of " + new TreeSet<>(INSTRUMENTS));
        return v;
    }

    private static String currency(String c) {
        String v = c == null || c.isBlank() ? "INR" : c.trim().toUpperCase();
        if (!v.matches("[A-Z]{3}")) throw bad("Currency must be a 3-letter code such as INR or USD");
        return v;
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }

    private static ResponseStatusException conflict(String m) {
        return new ResponseStatusException(HttpStatus.CONFLICT, m);
    }

    private void emit(Deposit d, String actor, String action) {
        kafka.send(TaskEvent.TOPIC, d.getTenantId().toString(), new TaskEvent(d.getTenantId(), actor, action, d.getCaseId(), Instant.now()));
    }
}

package com.tender.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tender.common.TaskEvent;
import com.tender.common.TenantContext;
import com.tender.common.TenantScope;
import com.tender.domain.CaseType;
import com.tender.domain.FlowCase;
import com.tender.repository.CaseTypeRepository;
import com.tender.repository.FlowCaseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class FlowService {
    public record TypeView(UUID id, String key, String name, Map<String, Object> definition) {
    }

    public record Action(String to, String label, boolean blocked) {
    }

    public record View(UUID id, String typeKey, String typeName, String title, String status, String statusLabel,
                       Map<String, Object> data,
                       LocalDate deadline, boolean open, String createdBy, Instant updatedAt, boolean dueSoon,
                       List<Action> actions) {
    }

    private static final Pattern KEY = Pattern.compile("[a-z0-9-]+");
    private final CaseTypeRepository types;
    private final FlowCaseRepository cases;
    private final TenantScope scope;
    private final KafkaTemplate<String, TaskEvent> kafka;
    private final ObjectMapper om;

    public FlowService(CaseTypeRepository types, FlowCaseRepository cases, TenantScope scope, KafkaTemplate<String, TaskEvent> kafka, ObjectMapper om) {
        this.types = types;
        this.cases = cases;
        this.scope = scope;
        this.kafka = kafka;
        this.om = om;
    }

    // ---------- case types and templates ----------
    @Transactional(readOnly = true)
    public List<TypeView> listTypes() {
        scope.apply();
        return types.findAllByOrderByNameAsc().stream().map(t -> new TypeView(t.getId(), t.getTypeKey(), t.getName(), asMap(json(t.getDefinition())))).toList();
    }

    @Transactional
    public TypeView install(String key) {
        scope.apply();
        if (!KEY.matcher(key).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad template name");
        if (types.findByTypeKey(key).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already installed");
        try (var in = getClass().getResourceAsStream("/templates/" + key + ".json")) {
            if (in == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such template");
            String raw = new String(in.readAllBytes());
            JsonNode d = json(raw);
            var t = types.save(new CaseType(TenantContext.require(), key, d.get("name").asText(), raw));
            return new TypeView(t.getId(), key, t.getName(), asMap(d));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Refreshes an installed template to the latest version shipped with the service. Existing cases keep their data. */
    @Transactional
    public TypeView upgrade(String key) {
        scope.apply();
        if (!KEY.matcher(key).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad template name");
        CaseType t = types.findByTypeKey(key).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Install the template first"));
        try (var in = getClass().getResourceAsStream("/templates/" + key + ".json")) {
            if (in == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such template");
            String raw = new String(in.readAllBytes());
            JsonNode d = json(raw);
            t.replaceDefinition(d.get("name").asText(), raw);
            return new TypeView(t.getId(), key, t.getName(), asMap(d));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- cases ----------
    @Transactional(readOnly = true)
    public List<View> list(String typeKey, Set<String> roles, String actor) {
        scope.apply();
        Map<UUID, CaseType> byId = new HashMap<>();
        types.findAll().forEach(t -> byId.put(t.getId(), t));
        return cases.findAllByOrderByUpdatedAtDesc().stream()
                .filter(c -> byId.containsKey(c.getTypeId()) && (typeKey == null || byId.get(c.getTypeId()).getTypeKey().equals(typeKey)))
                .map(c -> view(c, byId.get(c.getTypeId()), roles, actor)).toList();
    }

    @Transactional
    public View create(String typeKey, Map<String, Object> body, String actor, Set<String> roles) {
        scope.apply();
        JsonNode data = om.valueToTree(body);
        var t = types.findByTypeKey(typeKey).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Install the template first"));
        JsonNode d = json(t.getDefinition());
        if (!data.isObject()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad data");
        checkRequired(d, data);
        String initial = null;
        for (JsonNode s : d.get("states")) if (s.path("initial").asBoolean(false)) initial = s.get("key").asText();
        var c = cases.save(new FlowCase(TenantContext.require(), t.getId(), title(d, data), initial, data.toString(), deadline(d, data), actor));
        emit(c, actor, "CASE_CREATED");
        return view(c, t, roles, actor);
    }

    @Transactional
    public View updateData(UUID id, Map<String, Object> patchBody, String actor, Set<String> roles) {
        scope.apply();
        JsonNode patch = om.valueToTree(patchBody);
        var c = cases.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var t = types.findById(c.getTypeId()).orElseThrow();
        JsonNode d = json(t.getDefinition());
        ObjectNode merged = (ObjectNode) json(c.getData());
        if (patch.isObject()) merged.setAll((ObjectNode) patch);
        checkRequired(d, merged);
        c.update(merged.toString(), deadline(d, merged), title(d, merged));
        emit(c, actor, "CASE_UPDATED");
        return view(c, t, roles, actor);
    }

    /**
     * Moves a case along a transition defined in its type. Approval steps need a different person from the creator (four-eyes).
     */
    @Transactional
    public View transition(UUID id, String to, String actor, Set<String> roles) {
        scope.apply();
        var c = cases.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var t = types.findById(c.getTypeId()).orElseThrow();
        JsonNode d = json(t.getDefinition()), tr = null;
        for (JsonNode x : d.get("transitions"))
            if (x.get("from").asText().equals(c.getStatus()) && x.get("to").asText().equals(to)) tr = x;
        if (tr == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That step is not allowed from " + c.getStatus());
        if (!allowed(tr, roles))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your role cannot do this step");
        if (tr.path("approval").asBoolean(false) && actor.equals(c.getCreatedBy()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A second person must approve this step");
        c.moveTo(to, !terminal(d, to));
        emit(c, actor, "CASE_" + to);
        return view(c, t, roles, actor);
    }

    // ---------- deadline alerts (called per tenant by DeadlineJob) ----------
    @Transactional(readOnly = true)
    public List<UUID> dueTenants() {
        return cases.dueTenants();
    }

    @Transactional
    public int alertDue() {
        scope.apply();
        var due = cases.findByOpenTrueAndAlertedFalseAndDeadlineLessThanEqual(LocalDate.now().plusDays(3));
        due.forEach(c -> {
            c.markAlerted();
            emit(c, "system", "CASE_DEADLINE");
        });
        return due.size();
    }

    // ---------- helpers ----------
    private View view(FlowCase c, CaseType t, Set<String> roles, String actor) {
        JsonNode d = json(t.getDefinition());
        String label = c.getStatus();
        for (JsonNode s : d.get("states"))
            if (s.get("key").asText().equals(c.getStatus())) label = s.path("label").asText(label);
        List<Action> actions = new ArrayList<>();
        for (JsonNode x : d.get("transitions"))
            if (x.get("from").asText().equals(c.getStatus()) && allowed(x, roles))
                actions.add(new Action(x.get("to").asText(), x.path("label").asText(x.get("to").asText()), x.path("approval").asBoolean(false) && actor.equals(c.getCreatedBy())));
        boolean soon = c.isOpen() && c.getDeadline() != null && !c.getDeadline().isAfter(LocalDate.now().plusDays(3));
        return new View(c.getId(), t.getTypeKey(), t.getName(), c.getTitle(), c.getStatus(), label, asMap(json(c.getData())), c.getDeadline(), c.isOpen(), c.getCreatedBy(), c.getUpdatedAt(), soon, actions);
    }

    private boolean allowed(JsonNode tr, Set<String> roles) {
        if (roles.contains("ADMIN")) return true;
        for (JsonNode r : tr.path("roles")) if (roles.contains(r.asText().toUpperCase())) return true;
        return false;
    }

    private boolean terminal(JsonNode d, String key) {
        for (JsonNode s : d.get("states"))
            if (s.get("key").asText().equals(key)) return s.path("terminal").asBoolean(false);
        return false;
    }

    private void checkRequired(JsonNode d, JsonNode data) {
        for (JsonNode f : d.get("fields"))
            if (f.path("required").asBoolean(false)) {
                JsonNode v = data.get(f.get("key").asText());
                if (v == null || v.isNull() || v.asText().isBlank())
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing: " + f.path("label").asText());
            }
    }

    private String title(JsonNode d, JsonNode data) {
        return data.path(d.path("titleField").asText("title")).asText("Untitled");
    }

    private LocalDate deadline(JsonNode d, JsonNode data) {
        String k = d.path("deadlineField").asText(""), v = k.isEmpty() ? "" : data.path(k).asText("");
        if (v.isBlank()) return null;
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date");
        }
    }

    private Map<String, Object> asMap(JsonNode n) {
        return om.convertValue(n, new TypeReference<Map<String, Object>>() {
        });
    }

    private JsonNode json(String s) {
        try {
            return om.readTree(s);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void emit(FlowCase c, String actor, String action) {
        kafka.send(TaskEvent.TOPIC, c.getTenantId().toString(), new TaskEvent(c.getTenantId(), actor, action, c.getId(), Instant.now()));
    }
}

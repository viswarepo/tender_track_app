package com.tender.service;

import com.tender.common.*;
import com.tender.common.TenantContext;
import com.tender.common.TenantScope;
import com.tender.domain.Settings;
import com.tender.repository.SettingsRepository;
import org.slf4j.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final SettingsRepository repo;
    private final TenantScope scope;
    private final JavaMailSender mail;

    public NotificationService(SettingsRepository repo, TenantScope scope, JavaMailSender mail) {
        this.repo = repo;
        this.scope = scope;
        this.mail = mail;
    }

    @Transactional(readOnly = true)
    public String escalationEmail() {
        scope.apply();
        String escalationEmail = repo.findById(TenantContext.require()).map(Settings::getEscalationEmail).orElse("");

        return escalationEmail;
    }

    @Transactional
    public void saveEscalationEmail(String email) {
        scope.apply();
        repo.save(new Settings(TenantContext.require(), email));
    }

    @KafkaListener(topics = TaskEvent.TOPIC, groupId = "notification-service")
    @Transactional
    public void on(TaskEvent e) {
        if (!"TASK_ESCALATED".equals(e.action()) && !"CASE_DEADLINE".equals(e.action())) return;
        TenantContext.set(e.tenantId());
        try {
            scope.apply();
            repo.findById(e.tenantId()).map(Settings::getEscalationEmail).filter(s -> s != null && !s.isBlank()).ifPresent(to -> send(to, e));
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Emails never contain patient information, only a reference and a prompt to sign in.
     */
    private void send(String to, TaskEvent e) {
        try {
            var m = new SimpleMailMessage();
            m.setFrom("noreply@carelane.local");
            m.setTo(to);
            boolean deadline = "CASE_DEADLINE".equals(e.action());
            m.setSubject(deadline ? "A deadline is approaching" : "Overdue task needs attention");
            m.setText((deadline ? "A case in your organization has a deadline within 3 days (reference " : "A task in your organization is overdue and has been escalated (reference ") + e.taskId() + "). Sign in to Carelane to review it.");
            mail.send(m);
        } catch (Exception ex) {
            log.error("Could not send escalation email for tenant {}", e.tenantId(), ex);
        } // never requeue-loop on mail errors
    }
}

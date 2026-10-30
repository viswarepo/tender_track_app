package com.tender.identity.service;

import com.tender.identity.domain.Organization;
import com.tender.identity.domain.UserAccount;
import com.tender.identity.domain.UserAccount.Role;
import com.tender.identity.repository.OrganizationRepository;
import com.tender.identity.repository.UserAccountRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** On an empty database, creates two demo organizations. The ids match the sample data used by the other services. */
@Component
public class DemoData implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoData.class);

    private final UserAccountRepository users;
    private final OrganizationRepository orgs;
    private final PasswordEncoder encoder;
    private final boolean enabled;

    public DemoData(UserAccountRepository users, OrganizationRepository orgs, PasswordEncoder encoder,
                    @Value("${identity.bootstrap-demo:true}") boolean enabled) {
        this.users = users;
        this.orgs = orgs;
        this.encoder = encoder;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled || users.count() > 0) {
            return;
        }
        UUID apex = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID riverstone = UUID.fromString("22222222-2222-2222-2222-222222222222");
        orgs.save(new Organization(apex, "Apex Builders Pvt Ltd"));
        orgs.save(new Organization(riverstone, "Riverstone Infra Works"));
        users.save(new UserAccount(apex, "viswa", "viswa@example.com", "Viswa", encoder.encode("Viswa@987"), Role.ADMIN, false));
        users.save(new UserAccount(apex, "marcus", "marcus@example.com", "Marcus", encoder.encode("Tender@123"), Role.COORDINATOR, false));
        users.save(new UserAccount(riverstone, "elena", "elena@example.com", "Elena", encoder.encode("Tender@123"), Role.ADMIN, false));
        log.info("Created demo organizations and users: viswa (admin), marcus (coordinator), elena (admin of another organization)");
    }
}

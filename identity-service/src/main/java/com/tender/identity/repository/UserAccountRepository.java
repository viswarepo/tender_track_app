package com.tender.identity.repository;

import com.tender.identity.domain.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<UserAccount> findByOrgIdOrderByUsernameAsc(UUID orgId);

    /** Always look users up together with the organization, so one organization can never touch another's users. */
    Optional<UserAccount> findByIdAndOrgId(UUID id, UUID orgId);

    long countByOrgIdAndRoleAndEnabledTrue(UUID orgId, UserAccount.Role role);
}

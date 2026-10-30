package com.tender.identity.service;

import com.tender.identity.domain.UserAccount;
import com.tender.identity.domain.UserAccount.Role;
import com.tender.identity.repository.UserAccountRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** User management for one organization. Every method takes the caller's organization id from their token. */
@Service
public class UserService {
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;

    public UserService(UserAccountRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<AuthService.UserView> list(UUID orgId) {
        List<AuthService.UserView> list = users.findByOrgIdOrderByUsernameAsc(orgId).stream().map(AuthService::view).toList();
        return list;
    }

    @Transactional(readOnly = true)
    public AuthService.UserView me(UUID orgId, UUID userId) {
        return AuthService.view(find(orgId, userId));
    }

    @Transactional
    public AuthService.UserView create(UUID orgId, String username, String email, String displayName, String role, String password) {
        String name = AuthService.normalizeUsername(username);
        AuthService.checkPassword(password);
        if (users.existsByUsernameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already taken");
        }
        // the new person must choose their own password at first sign-in
        UserAccount user = users.save(new UserAccount(orgId, name, email.trim(), AuthService.blankToNull(displayName),
                encoder.encode(password), parseRole(role), true));
        return AuthService.view(user);
    }

    @Transactional
    public AuthService.UserView update(UUID orgId, UUID id, String email, String displayName, String role, Boolean enabled) {
        UserAccount user = find(orgId, id);
        Role newRole = role == null ? user.getRole() : parseRole(role);
        boolean newEnabled = enabled == null ? user.isEnabled() : enabled;
        boolean losesAdmin = user.getRole() == Role.ADMIN && user.isEnabled() && (newRole != Role.ADMIN || !newEnabled);
        if (losesAdmin && users.countByOrgIdAndRoleAndEnabledTrue(orgId, Role.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An organization needs at least one active admin");
        }
        user.setRole(newRole);
        user.setEnabled(newEnabled);
        user.updateProfile(email == null ? null : email.trim(), displayName);
        return AuthService.view(user);
    }

    @Transactional
    public void resetPassword(UUID orgId, UUID id, String newPassword) {
        AuthService.checkPassword(newPassword);
        find(orgId, id).setPassword(encoder.encode(newPassword), true);
    }

    private UserAccount find(UUID orgId, UUID id) {
        return users.findByIdAndOrgId(id, orgId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such user in your organization"));
    }

    private static Role parseRole(String role) {
        try {
            return Role.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be admin, coordinator or viewer");
        }
    }
}

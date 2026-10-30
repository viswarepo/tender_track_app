package com.tender.identity.service;

import com.tender.identity.domain.Organization;
import com.tender.identity.domain.UserAccount;
import com.tender.identity.repository.OrganizationRepository;
import com.tender.identity.repository.UserAccountRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    public record UserView(UUID id, String username, String email, String displayName, String role, boolean enabled,
                           boolean mustChangePassword, Instant lastLoginAt, Instant createdAt) {
    }

    public record LoginResult(String accessToken, long expiresIn, boolean mustChangePassword, UserView user) {
    }

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9._-]{3,40}$");
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_FOR = Duration.ofMinutes(15);

    private final UserAccountRepository users;
    private final OrganizationRepository orgs;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final boolean signupEnabled;
    private final String dummyHash;

    public AuthService(UserAccountRepository users, OrganizationRepository orgs, PasswordEncoder encoder, TokenService tokens,
                       @Value("${identity.signup-enabled:true}") boolean signupEnabled) {
        this.users = users;
        this.orgs = orgs;
        this.encoder = encoder;
        this.tokens = tokens;
        this.signupEnabled = signupEnabled;
        this.dummyHash = encoder.encode("not-a-real-password"); // so unknown usernames take as long to reject as real ones
    }

    /**
     * noRollbackFor matters: a wrong password throws an error, and without it the failed-attempt count
     * would be rolled back and the lockout would never trigger.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResult login(String username, String password) {
        String given = password == null ? "" : password;
        var found = users.findByUsernameIgnoreCase(username == null ? "" : username.trim());
        if (found.isEmpty()) {
            encoder.matches(given, dummyHash);
            throw denied();
        }
        UserAccount user = found.get();
        boolean passwordOk = encoder.matches(given, user.getPasswordHash());
        Organization org = orgs.findById(user.getOrgId()).orElse(null);
        if (user.isLocked() || !user.isEnabled() || org == null || !org.isActive()) {
            throw denied();
        }
        if (!passwordOk) {
            user.registerFailure(MAX_ATTEMPTS, LOCK_FOR);
            throw denied();
        }
        user.registerSuccess();
        return result(user, org.getName());
    }

    @Transactional
    public LoginResult register(String organizationName, String username, String email, String displayName, String password) {
        if (!signupEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sign-up is turned off. Ask an administrator for an account.");
        }
        String name = normalizeUsername(username);
        checkPassword(password);
        if (users.existsByUsernameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already taken");
        }
        Organization org = orgs.save(new Organization(UUID.randomUUID(), organizationName.trim()));
        UserAccount admin = users.save(new UserAccount(org.getId(), name, email.trim(), blankToNull(displayName),
                encoder.encode(password), UserAccount.Role.ADMIN, false));
        return result(admin, org.getName());
    }

    @Transactional
    public void changePassword(UUID userId, String current, String next) {
        UserAccount user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!encoder.matches(current, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your current password is wrong");
        }
        checkPassword(next);
        if (encoder.matches(next, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a password different from the current one");
        }
        user.setPassword(encoder.encode(next), false);
    }

    // ---- shared helpers ----
    public static void checkPassword(String password) {
        String p = password == null ? "" : password;
        if (p.length() < 8 || p.length() > 72 || !p.matches(".*[A-Za-z].*") || !p.matches(".*[0-9].*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The password must be 8 to 72 characters with at least one letter and one number");
        }
    }

    public static String normalizeUsername(String raw) {
        String name = raw == null ? "" : raw.trim().toLowerCase();
        if (!USERNAME.matcher(name).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The username must be 3 to 40 characters: lowercase letters, numbers, dot, dash or underscore");
        }
        return name;
    }

    public static UserView view(UserAccount u) {
        return new UserView(u.getId(), u.getUsername(), u.getEmail(), u.getDisplayName(), u.getRole().name().toLowerCase(),
                u.isEnabled(), u.isMustChangePassword(), u.getLastLoginAt(), u.getCreatedAt());
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private LoginResult result(UserAccount user, String orgName) {
        TokenService.Issued issued = tokens.issue(user, orgName);
        return new LoginResult(issued.token(), issued.expiresIn(), user.isMustChangePassword(), view(user));
    }

    private static ResponseStatusException denied() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong username or password, or the account is locked. Try again in a few minutes.");
    }
}

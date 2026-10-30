package com.tender.identity.web;

import com.tender.identity.service.AuthService;
import com.tender.identity.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
public class UserController {
    public record CreateReq(@NotBlank String username, @NotBlank @Email String email, String displayName,
                            @NotBlank String role, @NotBlank String password) {
    }

    public record UpdateReq(@Email String email, String displayName, String role, Boolean enabled) {
    }

    public record PasswordReq(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    public record ResetReq(@NotBlank String newPassword) {
    }

    private final UserService users;
    private final AuthService auth;

    public UserController(UserService users, AuthService auth) {
        this.users = users;
        this.auth = auth;
    }

    @GetMapping("/api/me")
    public AuthService.UserView me(Authentication a) {
        return users.me(org(a), uid(a));
    }

    @PutMapping("/api/me/password")
    public Map<String, Boolean> changePassword(@Valid @RequestBody PasswordReq r, Authentication a) {
        auth.changePassword(uid(a), r.currentPassword(), r.newPassword());
        return Map.of("changed", true);
    }

    @GetMapping("/api/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuthService.UserView> list(Authentication a) {
        return users.list(org(a));
    }

    @PostMapping("/api/users")
    @PreAuthorize("hasRole('ADMIN')")
    public AuthService.UserView create(@Valid @RequestBody CreateReq r, Authentication a) {
        return users.create(org(a), r.username(), r.email(), r.displayName(), r.role(), r.password());
    }

    @PutMapping("/api/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AuthService.UserView update(@PathVariable UUID id, @Valid @RequestBody UpdateReq r, Authentication a) {
        return users.update(org(a), id, r.email(), r.displayName(), r.role(), r.enabled());
    }

    @PostMapping("/api/users/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Boolean> reset(@PathVariable UUID id, @Valid @RequestBody ResetReq r, Authentication a) {
        users.resetPassword(org(a), id, r.newPassword());
        return Map.of("reset", true);
    }

    private static UUID org(Authentication a) {
        return UUID.fromString(((Jwt) a.getPrincipal()).getClaimAsString("tenant_id"));
    }

    private static UUID uid(Authentication a) {
        return UUID.fromString(((Jwt) a.getPrincipal()).getSubject());
    }
}

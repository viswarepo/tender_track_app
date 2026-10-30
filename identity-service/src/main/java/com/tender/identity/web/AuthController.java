package com.tender.identity.web;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.tender.identity.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    public record LoginReq(@NotBlank String username, @NotBlank String password) {
    }

    public record RegisterReq(@NotBlank @Size(min = 2, max = 80) String organizationName, @NotBlank String username,
                              @NotBlank @Email String email, String displayName, @NotBlank String password) {
    }

    private final AuthService auth;
    private final RSAKey key;

    public AuthController(AuthService auth, RSAKey key) {
        this.auth = auth;
        this.key = key;
    }

    @PostMapping("/auth/login")
    public AuthService.LoginResult login(@Valid @RequestBody LoginReq r) {
        return auth.login(r.username(), r.password());
    }

    @PostMapping("/auth/register")
    public AuthService.LoginResult register(@Valid @RequestBody RegisterReq r) {
        return auth.register(r.organizationName(), r.username(), r.email(), r.displayName(), r.password());
    }

    /** Public keys the other services use to check login tokens. */
    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return new JWKSet(key.toPublicJWK()).toJSONObject();
    }
}

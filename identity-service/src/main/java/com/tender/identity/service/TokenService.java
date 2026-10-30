package com.tender.identity.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.tender.identity.domain.UserAccount;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues signed login tokens. The claims match what the other services already read:
 * tenant_id, preferred_username and realm_access.roles.
 */
@Service
public class TokenService {
    public record Issued(String token, long expiresIn) {
    }

    private final RSAKey key;
    private final String issuer;
    private final Duration lifetime;

    public TokenService(RSAKey key, @Value("${identity.issuer}") String issuer, @Value("${identity.token-minutes:30}") long minutes) {
        this.key = key;
        this.issuer = issuer;
        this.lifetime = Duration.ofMinutes(minutes);
    }

    public Issued issue(UserAccount user, String organizationName) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(user.getId().toString())
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(lifetime)))
                    .claim("preferred_username", user.getUsername())
                    .claim("name", user.getDisplayName() == null ? user.getUsername() : user.getDisplayName())
                    .claim("tenant_id", user.getOrgId().toString())
                    .claim("org_name", organizationName)
                    .claim("realm_access", Map.of("roles", List.of(user.getRole().name().toLowerCase())))
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(), claims);
            jwt.sign(new RSASSASigner(key));
            return new Issued(jwt.serialize(), lifetime.toSeconds());
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign the login token", e);
        }
    }
}

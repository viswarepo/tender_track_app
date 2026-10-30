package com.tender.identity.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.ParseException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class KeyConfig {

    /** The signing key. Created on first start and saved, so logins survive restarts. Keep the file secret. */
    @Bean
    public RSAKey rsaKey(@Value("${identity.key-file:./data/jwt-key.json}") String file) throws IOException, ParseException, JOSEException {
        Path path = Path.of(file).toAbsolutePath();
        if (Files.exists(path)) {
            return RSAKey.parse(Files.readString(path));
        }
        RSAKey key = new RSAKeyGenerator(2048)
                .keyID(UUID.randomUUID().toString())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .generate();
        Files.createDirectories(path.getParent());
        Files.writeString(path, key.toJSONString());
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (Exception ignored) {
            // not a POSIX file system (for example Windows): rely on folder permissions
        }
        return key;
    }

    /** Lets this service verify the tokens it issued, for its own protected endpoints. */
    @Bean
    public JwtDecoder jwtDecoder(RSAKey key, @Value("${identity.issuer}") String issuer) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
}

package com.tender.common;

import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
                .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health").permitAll().anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter())))
                .addFilterAfter(new TenantFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @SuppressWarnings("unchecked")
    JwtAuthenticationConverter converter() {
        var c = new JwtAuthenticationConverter();
        c.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> ra = jwt.getClaim("realm_access");
            Collection<String> roles = ra == null ? List.of() : (Collection<String>) ra.get("roles");
            return roles.stream().map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r.toUpperCase())).toList();
        });
        return c;
    }
}
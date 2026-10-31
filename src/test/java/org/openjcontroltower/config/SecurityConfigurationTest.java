package org.openjcontroltower.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigurationTest {
    private final SecurityConfiguration configuration=new SecurityConfiguration();
    private static List<String> authorities(Jwt jwt) {
        return new SecurityConfiguration().jwtConverter().convert(jwt).getAuthorities().stream()
            // Spring Security 7 also adds FACTOR_* authorities; only roles and scopes are asserted here.
            .map(GrantedAuthority::getAuthority).filter(a->!a.startsWith("FACTOR_")).sorted().toList();
    }
    private static Jwt.Builder token() { return Jwt.withTokenValue("t").header("alg","none").subject("user"); }

    @Test void mapsKeycloakRealmRolesAndScopes() {
        Jwt jwt=token().claim("scope","profile").claim("realm_access",Map.of("roles",List.of("controltower-editor","offline"))).build();
        assertEquals(List.of("ROLE_controltower-editor","ROLE_offline","SCOPE_profile"),authorities(jwt));
    }
    @Test void toleratesMissingOrMalformedRealmAccess() {
        assertEquals(List.of("SCOPE_profile"),authorities(token().claim("scope","profile").build()));
        assertEquals(List.of(),authorities(token().claim("realm_access",Map.of("roles","not-a-list")).build()));
    }
    @Test void exposesConverterBean() { assertNotNull(configuration.jwtConverter()); }
}

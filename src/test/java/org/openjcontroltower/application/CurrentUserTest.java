package org.openjcontroltower.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CurrentUserTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private static Jwt.Builder token() { return Jwt.withTokenValue("t").header("alg","none").subject("subject-id"); }

    @Test void isAnonymousWithoutAuthentication() {
        assertEquals(new CurrentUser(null,List.of()),CurrentUser.get());
    }
    @Test void prefersTheKeycloakUsernameAndKeepsOnlyRoles() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token().claim("preferred_username","alice").build(),
            List.of(new SimpleGrantedAuthority("SCOPE_profile"),new SimpleGrantedAuthority("ROLE_b"),new SimpleGrantedAuthority("ROLE_a"))));
        CurrentUser user=CurrentUser.get();
        assertEquals(new CurrentUser("alice",List.of("a","b")),user);
        assertTrue(user.hasRole("a"));
        assertFalse(user.hasRole("profile"));
    }
    @Test void fallsBackToThePrincipalName() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token().build(),List.of()));
        assertEquals("subject-id",CurrentUser.get().name());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("bob","pw","ROLE_x"));
        assertEquals(new CurrentUser("bob",List.of("x")),CurrentUser.get());
    }
}

package org.openjcontroltower.application;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.List;

/** The signed-in user of the current request, as policies and audit fields see them. */
public record CurrentUser(String name,List<String> roles) {
    public static CurrentUser get() {
        Authentication authentication=SecurityContextHolder.getContext().getAuthentication();
        if (authentication==null) return new CurrentUser(null,List.of());
        String name=authentication instanceof JwtAuthenticationToken token
            && token.getToken().getClaimAsString("preferred_username")!=null
            ? token.getToken().getClaimAsString("preferred_username") : authentication.getName();
        List<String> roles=authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
            .filter(a->a.startsWith("ROLE_")).map(a->a.substring("ROLE_".length())).sorted().toList();
        return new CurrentUser(name,roles);
    }
    public boolean hasRole(String role) { return roles.contains(role); }
}

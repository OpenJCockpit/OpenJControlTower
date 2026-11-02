package org.openjcontroltower.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import java.util.*;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain security(HttpSecurity http,@Value("${CONTROLTOWER_EDITOR_ROLE:controltower-editor}") String editor,
        @Value("${CONTROLTOWER_ADMIN_ROLE:controltower-admin}") String admin) throws Exception {
        http.csrf(csrf->csrf.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth->auth
                .requestMatchers("/actuator/health","/actuator/health/**","/actuator/info","/actuator/prometheus","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll()
                // The web application reads the Keycloak settings before anyone has signed in.
                .requestMatchers(HttpMethod.GET,"/api/public/auth-config").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/**").authenticated()
                .requestMatchers("/api/settings/**").hasAuthority("ROLE_"+admin)
                .requestMatchers("/api/**").hasAnyAuthority("ROLE_"+editor,"ROLE_"+admin)
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth->oauth.jwt(jwt->jwt.jwtAuthenticationConverter(jwtConverter())));
        return http.build();
    }
    @Bean JwtAuthenticationConverter jwtConverter() {
        JwtAuthenticationConverter converter=new JwtAuthenticationConverter();
        JwtGrantedAuthoritiesConverter scopes=new JwtGrantedAuthoritiesConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt->{
            Collection<GrantedAuthority> authorities=new ArrayList<>(scopes.convert(jwt));
            Map<String,Object> realm=jwt.getClaimAsMap("realm_access");
            if (realm!=null && realm.get("roles") instanceof Collection<?> roles)
                roles.forEach(role->authorities.add(new SimpleGrantedAuthority("ROLE_"+role)));
            return authorities;
        });
        return converter;
    }
    @Bean OAuth2AuthorizedClientManager clientManager(ClientRegistrationRepository registrations,
        OAuth2AuthorizedClientService service) {
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager=new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations,service);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }
}

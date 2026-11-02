package org.openjcontroltower.config;

import org.openjcontroltower.application.SettingsService;
import org.openjcontroltower.domain.Settings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import java.util.function.Function;

/**
 * Validates tokens against the Keycloak instance from the settings, so a change on the settings page applies
 * without a restart. The signing keys are fetched again when the issuer or JWKS URL changes.
 */
@Component
public class SettingsJwtDecoder implements JwtDecoder {
    private record Cached(String issuer,String jwksUrl,JwtDecoder decoder) {}
    private final SettingsService settings;
    private final Function<Settings.Keycloak,JwtDecoder> factory;
    private volatile Cached cached;

    @Autowired
    public SettingsJwtDecoder(SettingsService settings) { this(settings,SettingsJwtDecoder::nimbus); }
    SettingsJwtDecoder(SettingsService settings,Function<Settings.Keycloak,JwtDecoder> factory) {
        this.settings=settings;this.factory=factory;
    }

    @Override
    public Jwt decode(String token) {
        Settings.Keycloak keycloak=settings.keycloak();
        Cached current=cached;
        if (current==null || !current.issuer().equals(keycloak.issuer()) || !current.jwksUrl().equals(keycloak.effectiveJwksUrl()))
            cached=current=new Cached(keycloak.issuer(),keycloak.effectiveJwksUrl(),factory.apply(keycloak));
        return current.decoder().decode(token);
    }
    static JwtDecoder nimbus(Settings.Keycloak keycloak) {
        NimbusJwtDecoder decoder=NimbusJwtDecoder.withJwkSetUri(keycloak.effectiveJwksUrl()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(keycloak.issuer()));
        return decoder;
    }
}

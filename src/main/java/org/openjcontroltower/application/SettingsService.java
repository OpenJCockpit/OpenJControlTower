package org.openjcontroltower.application;

import org.openjcontroltower.domain.Settings;
import org.openjcontroltower.infrastructure.DataStore;
import org.openjcontroltower.infrastructure.KeycloakProbe;
import org.openjcontroltower.infrastructure.OpenPolicyAgentClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Settings that administrators change at runtime. Environment values are the defaults until a value is saved.
 * Values are cached: the backend runs as a single replica, and every change goes through this service.
 */
@Service
public class SettingsService {
    static final String KEYCLOAK="keycloak", POLICY="policy";
    private final DataStore store;
    private final KeycloakProbe keycloakProbe;
    private final OpenPolicyAgentClient opa;
    private final Settings.Keycloak keycloakDefault;
    private final Settings.Policy policyDefault;
    private final boolean ignoreSavedKeycloak;
    private volatile Settings.Keycloak keycloak;
    private volatile Settings.Policy policy;

    public SettingsService(DataStore store,KeycloakProbe keycloakProbe,OpenPolicyAgentClient opa,
        @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
        @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwksUrl,
        @Value("${controltower.keycloak.client-id}") String clientId,
        @Value("${controltower.keycloak.ignore-saved}") boolean ignoreSavedKeycloak,
        @Value("${controltower.policy.enabled}") boolean policyEnabled,
        @Value("${controltower.policy.url}") String policyUrl,
        @Value("${controltower.policy.decision-path}") String decisionPath,
        @Value("${controltower.policy.fail-open}") boolean failOpen) {
        this.store=store;this.keycloakProbe=keycloakProbe;this.opa=opa;
        this.keycloakDefault=Settings.Keycloak.fromIssuer(issuer,clientId,jwksUrl);
        this.ignoreSavedKeycloak=ignoreSavedKeycloak;
        this.policyDefault=new Settings.Policy(policyEnabled,policyUrl,decisionPath,failOpen);
    }

    /** With {@code KEYCLOAK_IGNORE_SAVED_SETTINGS=true} the environment wins: the way back after a broken sign-in. */
    public Settings.Keycloak keycloak() {
        if (ignoreSavedKeycloak) return keycloakDefault;
        Settings.Keycloak current=keycloak;
        if (current==null) keycloak=current=store.setting(KEYCLOAK,Settings.Keycloak.class).orElse(keycloakDefault);
        return current;
    }
    public Settings.Policy policy() {
        Settings.Policy current=policy;
        if (current==null) policy=current=store.setting(POLICY,Settings.Policy.class).orElse(policyDefault);
        return current;
    }

    public KeycloakProbe.Result testKeycloak(Settings.Keycloak input) {
        return keycloakProbe.check(input.validated().effectiveJwksUrl());
    }
    /** Refuses settings whose signing keys cannot be fetched: they would reject every sign-in, including the admin's. */
    public Settings.Keycloak updateKeycloak(Settings.Keycloak input) {
        if (ignoreSavedKeycloak)
            throw new IllegalStateException("Keycloak settings come from the environment while KEYCLOAK_IGNORE_SAVED_SETTINGS is true");
        Settings.Keycloak validated=input.validated();
        KeycloakProbe.Result result=keycloakProbe.check(validated.effectiveJwksUrl());
        if (!result.reachable()) throw new IllegalArgumentException("Keycloak settings not saved. "+result.message());
        store.saveSetting(KEYCLOAK,validated,CurrentUser.get().name());
        keycloak=validated;
        return validated;
    }

    public OpenPolicyAgentClient.Status testPolicy(Settings.Policy input) {
        return opa.status(input.validated());
    }
    /** An enabled agent must answer first: with fail-closed, an unreachable agent would deny every governed action. */
    public Settings.Policy updatePolicy(Settings.Policy input) {
        Settings.Policy validated=input.validated();
        if (validated.enabled()) {
            OpenPolicyAgentClient.Status status=opa.status(validated);
            if (!status.reachable()) throw new IllegalArgumentException("Policy settings not saved. "+status.message());
        }
        store.saveSetting(POLICY,validated,CurrentUser.get().name());
        policy=validated;
        return validated;
    }
}

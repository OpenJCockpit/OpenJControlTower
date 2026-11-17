package org.openjcontroltower.application;

import org.openjcontroltower.domain.Settings;
import org.openjcontroltower.infrastructure.DataStore;
import org.openjcontroltower.infrastructure.OpenPolicyAgentClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Policy settings that administrators change at runtime. Environment values are the defaults until a value is saved.
 * Values are cached: the backend runs as a single replica, and every change goes through this service.
 */
@Service
public class SettingsService {
    static final String POLICY="policy";
    private final DataStore store;
    private final OpenPolicyAgentClient opa;
    private final Settings.Policy policyDefault;
    private volatile Settings.Policy policy;

    public SettingsService(DataStore store,OpenPolicyAgentClient opa,
        @Value("${controltower.policy.enabled}") boolean policyEnabled,
        @Value("${controltower.policy.url}") String policyUrl,
        @Value("${controltower.policy.decision-path}") String decisionPath,
        @Value("${controltower.policy.fail-open}") boolean failOpen) {
        this.store=store;this.opa=opa;
        this.policyDefault=new Settings.Policy(policyEnabled,policyUrl,decisionPath,failOpen);
    }

    public Settings.Policy policy() {
        Settings.Policy current=policy;
        if (current==null) policy=current=store.setting(POLICY,Settings.Policy.class).orElse(policyDefault);
        return current;
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

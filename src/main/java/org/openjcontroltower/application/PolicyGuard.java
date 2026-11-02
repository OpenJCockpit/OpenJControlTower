package org.openjcontroltower.application;

import org.openjcontroltower.domain.Settings;
import org.openjcontroltower.infrastructure.OpenPolicyAgentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Policy enforcement point: governed steps of the process ask Open Policy Agent before they act.
 * Input: {@code {action, user: {name, roles}, resource}}. When the agent is disabled, every action is allowed.
 */
@Service
public class PolicyGuard {
    /** Thrown when a policy denies an action, or when the agent cannot be reached and the settings fail closed. */
    public static class PolicyDeniedException extends RuntimeException {
        public PolicyDeniedException(String message) { super(message); }
    }
    private static final Logger log=LoggerFactory.getLogger(PolicyGuard.class);
    private final SettingsService settings;
    private final OpenPolicyAgentClient opa;
    public PolicyGuard(SettingsService settings,OpenPolicyAgentClient opa) { this.settings=settings;this.opa=opa; }

    public void check(String action,Map<String,Object> resource) {
        Settings.Policy policy=settings.policy();
        if (!policy.enabled()) return;
        CurrentUser user=CurrentUser.get();
        Map<String,Object> subject=new LinkedHashMap<>();
        subject.put("name",user.name());
        subject.put("roles",user.roles());
        Map<String,Object> input=new LinkedHashMap<>();
        input.put("action",action);
        input.put("user",subject);
        input.put("resource",resource);
        OpenPolicyAgentClient.Decision decision;
        try { decision=opa.decide(policy,input); }
        catch (RestClientException e) {
            if (policy.failOpen()) {
                log.warn("Open Policy Agent unreachable; allowing {} because the settings fail open: {}",action,e.getMessage());
                return;
            }
            throw new PolicyDeniedException("Open Policy Agent could not be reached, so "+action+" is denied: "+e.getMessage());
        }
        if (!decision.allow()) throw new PolicyDeniedException("Denied by policy: "+String.join("; ",decision.reasons()));
    }

    /** Builds a resource map from key-value pairs; values may be null. */
    public static Map<String,Object> resource(Object... keysAndValues) {
        Map<String,Object> resource=new LinkedHashMap<>();
        for (int i=0;i<keysAndValues.length;i+=2) resource.put((String)keysAndValues[i],keysAndValues[i+1]);
        return resource;
    }
}

package org.openjcontroltower.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openjcontroltower.domain.Settings;
import org.openjcontroltower.infrastructure.OpenPolicyAgentClient;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.ResourceAccessException;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PolicyGuardTest {
    private final SettingsService settings=mock(SettingsService.class);
    private final OpenPolicyAgentClient opa=mock(OpenPolicyAgentClient.class);
    private final PolicyGuard guard=new PolicyGuard(settings,opa);
    private final Settings.Policy enabled=new Settings.Policy(true,"http://opa:8181","openjcontroltower/decision",false);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void allowsEverythingWhileDisabled() {
        when(settings.policy()).thenReturn(new Settings.Policy(false,"http://opa:8181","p/d",false));
        guard.check("spec.approve",Map.of());
        verifyNoInteractions(opa);
    }
    @Test void sendsActionUserAndResourceAndAcceptsAnAllow() {
        when(settings.policy()).thenReturn(enabled);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice","pw","ROLE_controltower-editor"));
        when(opa.decide(eq(enabled),anyMap())).thenReturn(new OpenPolicyAgentClient.Decision(true,List.of()));
        guard.check("spec.approve",PolicyGuard.resource("id","A","createdBy",null));
        verify(opa).decide(enabled,Map.of("action","spec.approve",
            "user",Map.of("name","alice","roles",List.of("controltower-editor")),
            "resource",PolicyGuard.resource("id","A","createdBy",null)));
    }
    @Test void reportsTheReasonsOfADenial() {
        when(settings.policy()).thenReturn(enabled);
        when(opa.decide(eq(enabled),anyMap())).thenReturn(new OpenPolicyAgentClient.Decision(false,List.of("one","two")));
        assertEquals("Denied by policy: one; two",assertThrows(PolicyGuard.PolicyDeniedException.class,
            ()->guard.check("queue.dispatch",Map.of())).getMessage());
    }
    @Test void failsClosedOrOpenWhenTheAgentIsUnreachable() {
        when(opa.decide(any(),anyMap())).thenThrow(new ResourceAccessException("Connection refused"));
        when(settings.policy()).thenReturn(enabled);
        assertEquals("Open Policy Agent could not be reached, so queue.dispatch is denied: Connection refused",
            assertThrows(PolicyGuard.PolicyDeniedException.class,()->guard.check("queue.dispatch",Map.of())).getMessage());
        when(settings.policy()).thenReturn(new Settings.Policy(true,"http://opa:8181","p/d",true));
        assertDoesNotThrow(()->guard.check("queue.dispatch",Map.of()));
    }
}

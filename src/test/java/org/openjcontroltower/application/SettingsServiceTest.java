package org.openjcontroltower.application;

import org.junit.jupiter.api.Test;
import org.openjcontroltower.domain.Settings;
import org.openjcontroltower.infrastructure.DataStore;
import org.openjcontroltower.infrastructure.OpenPolicyAgentClient;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SettingsServiceTest {
    private final DataStore store=mock(DataStore.class);
    private final OpenPolicyAgentClient opa=mock(OpenPolicyAgentClient.class);
    private final SettingsService service=new SettingsService(store,opa,false,"http://opa:8181","openjcontroltower/decision",false);
    private final Settings.Policy enabled=new Settings.Policy(true,"http://opa:8181","openjcontroltower/decision",false);

    @Test void usesTheEnvironmentUntilSomethingIsSavedAndCachesTheResult() {
        when(store.setting(anyString(),any())).thenReturn(Optional.empty());
        assertEquals(new Settings.Policy(false,"http://opa:8181","openjcontroltower/decision",false),service.policy());
        service.policy();
        verify(store,times(1)).setting("policy",Settings.Policy.class);
    }
    @Test void prefersStoredSettings() {
        when(store.setting("policy",Settings.Policy.class)).thenReturn(Optional.of(enabled));
        assertEquals(enabled,service.policy());
    }
    @Test void savesAnEnabledAgentOnlyWhenItAnswers() {
        when(opa.status(enabled)).thenReturn(new OpenPolicyAgentClient.Status(false,"Could not reach",List.of()),
            new OpenPolicyAgentClient.Status(true,"Connected",List.of("decision.rego")));
        assertEquals("Policy settings not saved. Could not reach",
            assertThrows(IllegalArgumentException.class,()->service.updatePolicy(enabled)).getMessage());
        assertEquals(enabled,service.updatePolicy(enabled));
        verify(store).saveSetting("policy",enabled,null);
        assertEquals(enabled,service.policy());
        Settings.Policy disabled=new Settings.Policy(false,"http://unreachable:1","p/d",true);
        assertEquals(disabled,service.updatePolicy(disabled));
        verify(opa,never()).status(disabled);
    }
    @Test void testsPolicySettingsWithoutSaving() {
        when(opa.status(enabled)).thenReturn(new OpenPolicyAgentClient.Status(true,"Connected",List.of()));
        assertTrue(service.testPolicy(new Settings.Policy(true,"http://opa:8181/","openjcontroltower/decision",false)).reachable());
        verifyNoInteractions(store);
    }
}

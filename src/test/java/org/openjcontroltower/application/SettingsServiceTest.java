package org.openjcontroltower.application;

import org.junit.jupiter.api.Test;
import org.openjcontroltower.domain.Settings;
import org.openjcontroltower.infrastructure.DataStore;
import org.openjcontroltower.infrastructure.KeycloakProbe;
import org.openjcontroltower.infrastructure.OpenPolicyAgentClient;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SettingsServiceTest {
    private final DataStore store=mock(DataStore.class);
    private final KeycloakProbe probe=mock(KeycloakProbe.class);
    private final OpenPolicyAgentClient opa=mock(OpenPolicyAgentClient.class);
    private final SettingsService service=service(false);
    private SettingsService service(boolean ignoreSavedKeycloak) {
        return new SettingsService(store,probe,opa,"http://localhost:8081/realms/ojct","http://keycloak:8081/realms/ojct/protocol/openid-connect/certs",
            "web",ignoreSavedKeycloak,false,"http://opa:8181","openjcontroltower/decision",false);
    }
    private final Settings.Keycloak other=new Settings.Keycloak("https://id.example.org","eng","app",null);
    private final Settings.Policy enabled=new Settings.Policy(true,"http://opa:8181","openjcontroltower/decision",false);

    @Test void usesTheEnvironmentUntilSomethingIsSavedAndCachesTheResult() {
        when(store.setting(anyString(),any())).thenReturn(Optional.empty());
        assertEquals(new Settings.Keycloak("http://localhost:8081","ojct","web","http://keycloak:8081"),service.keycloak());
        assertEquals(new Settings.Policy(false,"http://opa:8181","openjcontroltower/decision",false),service.policy());
        service.keycloak();service.policy();
        verify(store,times(1)).setting("keycloak",Settings.Keycloak.class);
        verify(store,times(1)).setting("policy",Settings.Policy.class);
    }
    @Test void prefersStoredSettings() {
        when(store.setting("keycloak",Settings.Keycloak.class)).thenReturn(Optional.of(other));
        when(store.setting("policy",Settings.Policy.class)).thenReturn(Optional.of(enabled));
        assertEquals(other,service.keycloak());
        assertEquals(enabled,service.policy());
    }
    @Test void savesKeycloakSettingsOnlyWhenTheSigningKeysCanBeFetched() {
        when(probe.check("https://id.example.org/realms/eng/protocol/openid-connect/certs"))
            .thenReturn(new KeycloakProbe.Result(false,"Could not fetch"),new KeycloakProbe.Result(true,"Connected"));
        assertEquals("Keycloak settings not saved. Could not fetch",
            assertThrows(IllegalArgumentException.class,()->service.updateKeycloak(other)).getMessage());
        verify(store,never()).saveSetting(anyString(),any(),any());
        assertEquals(other,service.updateKeycloak(new Settings.Keycloak("https://id.example.org/","eng","app","")));
        verify(store).saveSetting("keycloak",other,null);
        assertEquals(other,service.keycloak());
        verify(store,never()).setting(anyString(),any());
    }
    @Test void ignoresSavedKeycloakSettingsForRecovery() {
        SettingsService recovery=service(true);
        assertEquals("http://localhost:8081",recovery.keycloak().url());
        assertThrows(IllegalStateException.class,()->recovery.updateKeycloak(other));
        verifyNoInteractions(store,probe);
    }
    @Test void testsKeycloakSettingsWithoutSaving() {
        when(probe.check(anyString())).thenReturn(new KeycloakProbe.Result(true,"Connected"));
        assertTrue(service.testKeycloak(other).reachable());
        assertThrows(IllegalArgumentException.class,()->service.testKeycloak(new Settings.Keycloak("x","eng","app",null)));
        verifyNoInteractions(store);
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

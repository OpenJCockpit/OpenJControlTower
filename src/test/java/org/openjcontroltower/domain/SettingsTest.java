package org.openjcontroltower.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
    @Test void derivesIssuerAndSigningKeysFromUrlAndRealm() {
        Settings.Keycloak keycloak=new Settings.Keycloak(" https://id.example.org/ "," eng "," web ","  ").validated();
        assertEquals(new Settings.Keycloak("https://id.example.org","eng","web",null),keycloak);
        assertEquals("https://id.example.org/realms/eng",keycloak.issuer());
        assertEquals("https://id.example.org/realms/eng/protocol/openid-connect/certs",keycloak.effectiveJwksUrl());
        Settings.Keycloak internal=new Settings.Keycloak("http://localhost:8081","eng","web","http://keycloak:8081/").validated();
        assertEquals("http://keycloak:8081/realms/eng/protocol/openid-connect/certs",internal.effectiveJwksUrl());
        assertNull(new Settings.Keycloak("https://id.example.org","eng","web",null).validated().backendUrl());
    }
    @Test void rejectsInvalidKeycloakSettings() {
        assertThrows(IllegalArgumentException.class,()->new Settings.Keycloak("https://id.example.org",null,"web",null).validated());
        assertThrows(IllegalArgumentException.class,()->new Settings.Keycloak("https://id.example.org","a/b","web",null).validated());
        assertThrows(IllegalArgumentException.class,()->new Settings.Keycloak("https://id.example.org","eng",null,null).validated());
        assertThrows(IllegalArgumentException.class,()->new Settings.Keycloak("https://id.example.org","eng","web client",null).validated());
        assertThrows(IllegalArgumentException.class,()->new Settings.Keycloak("ftp://id.example.org","eng","web",null).validated());
        assertThrows(IllegalArgumentException.class,()->new Settings.Keycloak("https://id.example.org","eng","web","nope").validated());
    }
    @Test void splitsAnIssuerIntoUrlAndRealm() {
        assertEquals(new Settings.Keycloak("http://localhost:8081","openjcontroltower","web",null),
            Settings.Keycloak.fromIssuer("http://localhost:8081/realms/openjcontroltower","web",""));
        assertEquals(new Settings.Keycloak("https://sso.example.org/auth","eng","web","http://keycloak:8080"),
            Settings.Keycloak.fromIssuer("https://sso.example.org/auth/realms/eng","web","http://keycloak:8080/realms/eng/protocol/openid-connect/certs"));
        assertNull(Settings.Keycloak.fromIssuer("https://sso.example.org/realms/eng","web",null).backendUrl());
        // The same address as the browser needs no backend override.
        assertNull(Settings.Keycloak.fromIssuer("https://sso.example.org/realms/eng","web",
            "https://sso.example.org/realms/eng/protocol/openid-connect/certs").backendUrl());
        assertThrows(IllegalStateException.class,()->Settings.Keycloak.fromIssuer("https://sso.example.org/eng","web",null));
        assertThrows(IllegalStateException.class,()->Settings.Keycloak.fromIssuer("https://sso.example.org/realms/eng","web",
            "https://sso.example.org/realms/other/protocol/openid-connect/certs"));
    }
    @Test void normalisesAndValidatesPolicySettings() {
        assertEquals(new Settings.Policy(true,"http://opa:8181","openjcontroltower/decision",false),
            new Settings.Policy(true," http://opa:8181/ ","/openjcontroltower/decision/",false).validated());
        assertThrows(IllegalArgumentException.class,()->new Settings.Policy(true,"http://opa:8181",null,false).validated());
        assertThrows(IllegalArgumentException.class,()->new Settings.Policy(true,"http://opa:8181","a/../b",false).validated());
        assertThrows(IllegalArgumentException.class,()->new Settings.Policy(true,null,"a/b",false).validated());
    }
    @Test void acceptsOnlyPlainHttpUrls() {
        assertEquals("https://h.example/p",Settings.httpUrl("https://h.example/p//","URL"));
        assertEquals("URL is not a valid URL",assertThrows(IllegalArgumentException.class,()->Settings.httpUrl("http://h x","URL")).getMessage());
        assertThrows(IllegalArgumentException.class,()->Settings.httpUrl("http:///path","URL"));
        assertThrows(IllegalArgumentException.class,()->Settings.httpUrl("mailto:a@b","URL"));
        for (String bad:new String[]{"https://u:p@h.example","https://h.example?q=1","https://h.example#f"})
            assertThrows(IllegalArgumentException.class,()->Settings.httpUrl(bad,"URL"),bad);
    }
}

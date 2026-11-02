package org.openjcontroltower.infrastructure;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class KeycloakProbeTest {
    private final KeycloakProbe probe=new KeycloakProbe(Duration.ofSeconds(2),Duration.ofSeconds(2));

    @Test void countsTheSigningKeys() throws Exception {
        try (StubHttpServer keycloak=new StubHttpServer().respond(200,"{\"keys\":[{\"kid\":\"a\"}]}")) {
            assertEquals(new KeycloakProbe.Result(true,"Connected: the realm publishes 1 signing key."),probe.check(keycloak.url()+"/certs"));
            assertEquals("/certs",keycloak.last().path());
            keycloak.respond(200,"{\"keys\":[{\"kid\":\"a\"},{\"kid\":\"b\"}]}");
            assertEquals("Connected: the realm publishes 2 signing keys.",probe.check(keycloak.url()+"/certs").message());
        }
    }
    @Test void rejectsAnswersWithoutKeys() throws Exception {
        try (StubHttpServer keycloak=new StubHttpServer().respond(200,"{\"keys\":[]}")) {
            assertEquals(new KeycloakProbe.Result(false,"The JWKS URL answers, but returns no signing keys: check the realm"),probe.check(keycloak.url()));
            keycloak.respond(200,"");
            assertFalse(probe.check(keycloak.url()).reachable());
        }
    }
    @Test void reportsErrors() throws Exception {
        try (StubHttpServer keycloak=new StubHttpServer().respond(404,"{\"error\":\"Realm does not exist\"}")) {
            KeycloakProbe.Result result=probe.check(keycloak.url()+"/certs");
            assertFalse(result.reachable());
            assertTrue(result.message().startsWith("Could not fetch the signing keys from "+keycloak.url()+"/certs: 404"),result.message());
        }
    }
}

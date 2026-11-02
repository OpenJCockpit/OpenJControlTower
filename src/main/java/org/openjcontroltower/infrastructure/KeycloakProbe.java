package org.openjcontroltower.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;

/** Checks that the backend can fetch Keycloak's signing keys, without which no sign-in would be accepted. */
@Component
public class KeycloakProbe {
    public record Result(boolean reachable,String message) {}
    private final RestClient rest;

    public KeycloakProbe(@Value("${controltower.http.connect-timeout}") Duration connectTimeout,
                         @Value("${controltower.http.read-timeout}") Duration readTimeout) {
        this.rest=HttpClients.withTimeouts(connectTimeout,readTimeout);
    }

    public Result check(String jwksUrl) {
        try {
            JsonNode keys=rest.get().uri(URI.create(jwksUrl)).retrieve().body(JsonNode.class);
            int count=keys==null?0:keys.path("keys").size();
            if (count==0) return new Result(false,"The JWKS URL answers, but returns no signing keys: check the realm");
            return new Result(true,"Connected: the realm publishes "+count+" signing key"+(count==1?"":"s")+".");
        } catch (RestClientException e) {
            return new Result(false,"Could not fetch the signing keys from "+jwksUrl+": "+e.getMessage());
        }
    }
}

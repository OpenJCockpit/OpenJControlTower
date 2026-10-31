package org.openjcontroltower.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** Narrow adapter for OpenJCockpit's OpenAPI queue item operation. */
@Component
public class CockpitQueueClient {
    private final boolean enabled;
    private final String baseUrl;
    private final OAuth2AuthorizedClientManager tokens;
    private final RestClient rest;
    public CockpitQueueClient(@Value("${controltower.cockpit.enabled}") boolean enabled,
        @Value("${controltower.cockpit.base-url}") String baseUrl, OAuth2AuthorizedClientManager tokens,
        @Value("${controltower.http.connect-timeout}") Duration connectTimeout,
        @Value("${controltower.http.read-timeout}") Duration readTimeout) {
        this.enabled=enabled;this.baseUrl=baseUrl.replaceAll("/+$","");this.tokens=tokens;
        this.rest=HttpClients.withTimeouts(connectTimeout,readTimeout);
    }
    public String enqueue(String projectId,String workflowId,String specFile) {
        if (!enabled) throw new IllegalStateException("OpenJCockpit dispatch is disabled in configuration");
        OAuth2AuthorizedClient client=tokens.authorize(OAuth2AuthorizeRequest.withClientRegistrationId("openjcockpit")
            .principal("openjcontroltower-service").build());
        if (client==null) throw new IllegalStateException("Unable to obtain OpenJCockpit service token");
        String path="/api/projects/"+UriUtils.encodePathSegment(projectId,StandardCharsets.UTF_8)+"/spec-queue/items";
        // Already encoded: pass a URI so RestClient does not encode the segment a second time.
        String result=rest.post().uri(URI.create(baseUrl+path)).headers(h->h.setBearerAuth(client.getAccessToken().getTokenValue()))
            .body(Map.of("specFile",specFile,"workflowId",workflowId)).retrieve().body(String.class);
        return result==null?"Accepted":result;
    }
}

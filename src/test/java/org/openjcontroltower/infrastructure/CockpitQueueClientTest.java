package org.openjcontroltower.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import java.time.Duration;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CockpitQueueClientTest {
    private final StubHttpServer server;
    private final OAuth2AuthorizedClientManager tokens=mock(OAuth2AuthorizedClientManager.class);
    CockpitQueueClientTest() throws Exception { server=new StubHttpServer(); }
    @AfterEach void stop() { server.close(); }

    private CockpitQueueClient client(boolean enabled,Duration readTimeout) {
        return new CockpitQueueClient(enabled,server.url()+"//",tokens,Duration.ofSeconds(2),readTimeout);
    }
    private void givenToken() {
        ClientRegistration registration=ClientRegistration.withRegistrationId("openjcockpit").clientId("svc")
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).tokenUri("http://idp/token").build();
        when(tokens.authorize(any())).thenReturn(new OAuth2AuthorizedClient(registration,"openjcontroltower-service",
            new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"service-token",Instant.now(),Instant.now().plusSeconds(60))));
    }
    @Test void refusesWhenDisabled() {
        assertThrows(IllegalStateException.class,()->client(false,Duration.ofSeconds(2)).enqueue("p","wf","001-a/spec.md"));
        verifyNoInteractions(tokens);
    }
    @Test void refusesWithoutServiceToken() {
        assertThrows(IllegalStateException.class,()->client(true,Duration.ofSeconds(2)).enqueue("p","wf","001-a/spec.md"));
    }
    @Test void postsQueueItemWithServiceToken() {
        givenToken();
        server.respond(201,"{\"id\":\"q-1\"}");
        assertEquals("{\"id\":\"q-1\"}",client(true,Duration.ofSeconds(2)).enqueue("team/a b","wf-7","001-a/spec.md"));
        StubHttpServer.Request request=server.last();
        assertEquals("POST",request.method());
        assertEquals("/api/projects/team%2Fa%20b/spec-queue/items",request.path());
        assertEquals("Bearer service-token",request.authorization());
        assertTrue(request.body().contains("\"specFile\":\"001-a/spec.md\""));
        assertTrue(request.body().contains("\"workflowId\":\"wf-7\""));
    }
    @Test void reportsAcceptedForEmptyBody() {
        givenToken();
        server.respond(202,"");
        assertEquals("Accepted",client(true,Duration.ofSeconds(2)).enqueue("p","wf","001-a/spec.md"));
    }
    @Test void propagatesHttpErrorsAndTimeouts() {
        givenToken();
        server.respond(500,"{}");
        assertThrows(RuntimeException.class,()->client(true,Duration.ofSeconds(2)).enqueue("p","wf","001-a/spec.md"));
        server.respond(201,"{}").delay(1500);
        assertThrows(RuntimeException.class,()->client(true,Duration.ofMillis(200)).enqueue("p","wf","001-a/spec.md"));
    }
}

package org.openjcontroltower.infrastructure;

import org.junit.jupiter.api.Test;
import org.openjcontroltower.domain.Settings;
import org.springframework.web.client.RestClientException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OpenPolicyAgentClientTest {
    private final OpenPolicyAgentClient client=new OpenPolicyAgentClient(Duration.ofSeconds(2),Duration.ofSeconds(2));
    private static Settings.Policy policy(StubHttpServer server) {
        return new Settings.Policy(true,server.url(),"openjcontroltower/decision",false);
    }

    @Test void postsTheInputAndReadsAnAllow() throws Exception {
        try (StubHttpServer opa=new StubHttpServer().respond(200,"{\"result\":{\"allow\":true,\"deny\":[]}}")) {
            assertEquals(new OpenPolicyAgentClient.Decision(true,List.of()),client.decide(policy(opa),Map.of("action","spec.approve")));
            assertEquals("POST",opa.last().method());
            assertEquals("/v1/data/openjcontroltower/decision",opa.last().path());
            assertEquals("{\"input\":{\"action\":\"spec.approve\"}}",opa.last().body());
        }
    }
    @Test void readsSortedDenyReasons() throws Exception {
        try (StubHttpServer opa=new StubHttpServer().respond(200,"{\"result\":{\"allow\":false,\"deny\":[\"b\",\"a\"]}}")) {
            assertEquals(new OpenPolicyAgentClient.Decision(false,List.of("a","b")),client.decide(policy(opa),Map.of()));
        }
    }
    @Test void deniesWhenTheDecisionIsMissingOrSaysNothing() throws Exception {
        try (StubHttpServer opa=new StubHttpServer().respond(200,"{}")) {
            assertEquals(List.of("The policy decision openjcontroltower/decision is not defined in Open Policy Agent"),
                client.decide(policy(opa),Map.of()).reasons());
            opa.respond(200,"{\"result\":true}");
            assertFalse(client.decide(policy(opa),Map.of()).allow());
            opa.respond(200,"");
            assertFalse(client.decide(policy(opa),Map.of()).allow());
            opa.respond(200,"{\"result\":{}}");
            assertEquals(new OpenPolicyAgentClient.Decision(false,List.of("Denied by policy openjcontroltower/decision")),
                client.decide(policy(opa),Map.of()));
        }
    }
    @Test void reportsTransportErrorsToTheCaller() throws Exception {
        try (StubHttpServer opa=new StubHttpServer().respond(500,"{}")) {
            assertThrows(RestClientException.class,()->client.decide(policy(opa),Map.of()));
        }
    }
    @Test void listsPoliciesAndConfirmsTheDecision() throws Exception {
        try (StubHttpServer opa=new StubHttpServer()
                .route("GET /v1/policies",200,"{\"result\":[{\"id\":\"/policies/spec_approval.rego\"},{\"id\":\"/policies/decision_test.rego\"},{\"id\":\"decision.rego\"}]}")
                .route("GET /v1/data/openjcontroltower/decision",200,"{\"result\":{\"allow\":false}}")) {
            assertEquals(new OpenPolicyAgentClient.Status(true,"Connected: 2 policies loaded and decision openjcontroltower/decision found.",
                List.of("decision.rego","spec_approval.rego")),client.status(policy(opa)));
        }
    }
    @Test void reportsAMissingDecisionOrAnUnreachableAgent() throws Exception {
        try (StubHttpServer opa=new StubHttpServer().respond(200,"{}").route("GET /v1/policies",200,"")) {
            assertEquals(new OpenPolicyAgentClient.Status(false,"Open Policy Agent answers, but has no decision at openjcontroltower/decision",List.of()),
                client.status(policy(opa)));
            opa.route("GET /v1/data/openjcontroltower/decision",200,"");
            assertFalse(client.status(policy(opa)).reachable());
        }
        StubHttpServer stopped=new StubHttpServer();
        Settings.Policy unreachable=policy(stopped);
        stopped.close();
        OpenPolicyAgentClient.Status status=client.status(unreachable);
        assertFalse(status.reachable());
        assertTrue(status.message().startsWith("Could not reach Open Policy Agent: "),status.message());
    }
}

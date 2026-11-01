package org.openjcontroltower.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openjcontroltower.domain.Models.Spec;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DecisionProviderTest {
    private final StubHttpServer server;
    private final Spec spec=new Spec("S","p","Login","login","APPROVED","x".repeat(2000),List.of(),"001-login/spec.md",null);
    DecisionProviderTest() throws Exception { server=new StubHttpServer(); }
    @AfterEach void stop() { server.close(); }

    private DecisionProvider provider(String name,String key) {
        return new DecisionProvider(name,server.url()+"/v1/systemone",key,Duration.ofSeconds(2),Duration.ofMillis(500));
    }
    private static String answer(String choice,double confidence) {
        return "{\"answers\":{\"priority\":{\"choice\":\""+choice+"\",\"confidence\":"+confidence+"}}}";
    }
    @Test void deterministicNeedsNoNetwork() {
        assertEquals(new DecisionProvider.Rank(50,"Deterministic default"),provider("DETERMINISTIC","").rank(spec));
        assertNull(server.last());
    }
    @Test void rejectsUnknownProviderOrMissingKey() {
        assertThrows(IllegalStateException.class,()->provider("other","key").rank(spec));
        assertThrows(IllegalStateException.class,()->provider("laya"," ").rank(spec));
    }
    @Test void mapsConfidentChoicesToScores() {
        server.respond(200,answer("urgent",0.9));
        DecisionProvider.Rank urgent=provider("laya","secret").rank(spec);
        assertEquals(90,urgent.score());assertEquals("laya confidence=0.9",urgent.reason());
        assertEquals("Bearer secret",server.last().authorization());
        assertTrue(server.last().body().contains("\"title\":\"Login\""));
        assertFalse(server.last().body().contains("x".repeat(1201)),"Spec body must be truncated");
        server.respond(200,answer("high",0.8));
        assertEquals(70,provider("JEV","secret").rank(spec).score());
        server.respond(200,answer("normal",0.99));
        assertEquals(50,provider("jev","secret").rank(spec).score());
    }
    @Test void fallsBackOnLowConfidenceEmptyResponseOrFailure() {
        server.respond(200,answer("urgent",0.5));
        assertEquals("Low confidence fallback",provider("laya","k").rank(spec).reason());
        server.respond(200,"");
        assertEquals("Decision service returned empty response",provider("laya","k").rank(spec).reason());
        server.respond(503,"{}");
        assertEquals(new DecisionProvider.Rank(50,"Decision service unavailable: deterministic fallback"),provider("laya","k").rank(spec));
        server.respond(200,answer("urgent",0.9)).delay(1500);
        assertEquals(50,provider("laya","k").rank(spec).score());
    }
}

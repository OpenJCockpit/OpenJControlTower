package org.openjcontroltower.infrastructure;

import tools.jackson.databind.JsonNode;
import org.openjcontroltower.domain.Models.Spec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import java.util.Map;

/** Laya/Jev may rank ready work, but never override a dependency. */
@Component
public class DecisionProvider {
    public record Rank(int score,String reason) {}
    private final String provider, endpoint, key;
    private final RestClient rest;
    public DecisionProvider(@Value("${controltower.decisions.provider}") String provider,
        @Value("${controltower.decisions.endpoint}") String endpoint,
        @Value("${controltower.decisions.api-key}") String key,
        @Value("${controltower.http.connect-timeout}") Duration connectTimeout,
        @Value("${controltower.http.read-timeout}") Duration readTimeout) {
        this.provider=provider;this.endpoint=endpoint;this.key=key;
        this.rest=HttpClients.withTimeouts(connectTimeout,readTimeout);
    }
    public Rank rank(Spec spec) {
        if (provider.equalsIgnoreCase("deterministic")) return new Rank(50,"Deterministic default");
        if (!(provider.equalsIgnoreCase("laya") || provider.equalsIgnoreCase("jev")) || key.isBlank())
            throw new IllegalStateException("Decision provider requires valid provider and API key");
        try {
            Map<String,Object> body=Map.of(
                "state",Map.of("title",spec.title(),"summary",spec.body().substring(0,Math.min(1200,spec.body().length()))),
                "questions",Map.of("priority",Map.of("type","choice",
                    "instructions","Select urgency based on security or direct business impact; prefer normal when unclear",
                    "criteria",Map.of("urgent","Confirmed security or production blocker",
                        "high","High immediate impact","normal","Ordinary planned development"))));
            JsonNode response=rest.post().uri(endpoint).headers(h->h.setBearerAuth(key))
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
            if (response==null) return new Rank(50,"Decision service returned empty response");
            JsonNode answer=response.path("answers").path("priority");
            if (answer.path("confidence").asDouble(0)<0.75) return new Rank(50,"Low confidence fallback");
            String choice=answer.path("choice").asText();
            return new Rank(switch(choice) { case "urgent" -> 90; case "high" -> 70; default -> 50; },
                provider+" confidence="+answer.path("confidence").asDouble());
        } catch (RuntimeException ex) {
            return new Rank(50,"Decision service unavailable: deterministic fallback");
        }
    }
}

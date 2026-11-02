package org.openjcontroltower.infrastructure;

import org.openjcontroltower.domain.Settings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Asks Open Policy Agent for decisions through its REST data API. */
@Component
public class OpenPolicyAgentClient {
    /** {@code reasons}: the deny messages of the policies, empty when allowed. */
    public record Decision(boolean allow,List<String> reasons) {}
    /** {@code policies}: the policy modules OPA has loaded, without test modules. */
    public record Status(boolean reachable,String message,List<String> policies) {}
    private final RestClient rest;

    public OpenPolicyAgentClient(@Value("${controltower.http.connect-timeout}") Duration connectTimeout,
                                 @Value("${controltower.http.read-timeout}") Duration readTimeout) {
        this.rest=HttpClients.withTimeouts(connectTimeout,readTimeout);
    }

    /** @throws RestClientException when OPA cannot be reached or answers with an error status */
    public Decision decide(Settings.Policy policy,Map<String,Object> input) {
        JsonNode response=rest.post().uri(URI.create(policy.url()+"/v1/data/"+policy.decisionPath()))
            .contentType(MediaType.APPLICATION_JSON).body(Map.of("input",input)).retrieve().body(JsonNode.class);
        JsonNode result=response==null?null:response.get("result");
        if (result==null || !result.isObject())
            return new Decision(false,List.of("The policy decision "+policy.decisionPath()+" is not defined in Open Policy Agent"));
        List<String> reasons=new ArrayList<>();
        result.path("deny").forEach(reason->reasons.add(reason.asText()));
        boolean allow=result.path("allow").asBoolean(false);
        if (!allow && reasons.isEmpty()) reasons.add("Denied by policy "+policy.decisionPath());
        return new Decision(allow,reasons.stream().sorted().toList());
    }

    /** Checks that OPA answers, lists its policies and confirms that the decision path exists. */
    public Status status(Settings.Policy policy) {
        try {
            JsonNode modules=rest.get().uri(URI.create(policy.url()+"/v1/policies")).retrieve().body(JsonNode.class);
            List<String> policies=new ArrayList<>();
            if (modules!=null) modules.path("result").forEach(module->{
                String id=module.path("id").asText();
                if (!id.endsWith("_test.rego")) policies.add(id.substring(id.lastIndexOf('/')+1));
            });
            JsonNode decision=rest.get().uri(URI.create(policy.url()+"/v1/data/"+policy.decisionPath())).retrieve().body(JsonNode.class);
            if (decision==null || decision.get("result")==null)
                return new Status(false,"Open Policy Agent answers, but has no decision at "+policy.decisionPath(),policies.stream().sorted().toList());
            return new Status(true,"Connected: "+policies.size()+" policies loaded and decision "+policy.decisionPath()+" found.",
                policies.stream().sorted().toList());
        } catch (RestClientException e) {
            return new Status(false,"Could not reach Open Policy Agent: "+e.getMessage(),List.of());
        }
    }
}

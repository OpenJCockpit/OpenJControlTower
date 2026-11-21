package org.openjcontroltower;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openjcontroltower.infrastructure.DataStore;
import org.openjcontroltower.infrastructure.GitConnectionChecker;
import org.openjcontroltower.infrastructure.GitJobRunner;
import org.openjcontroltower.infrastructure.OpenPolicyAgentClient;
import org.openjcontroltower.domain.Settings;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Boots the full application on H2 with Flyway, Spring Security and the demo assistant. */
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:integration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "controltower.cockpit.enabled=false",
    "controltower.git.credentials.secret=integration-test-secret-of-32-chars!",
    "controltower.git.credentials.salt=0123456789abcdef"})
class ControlTowerIntegrationTest {
    /** The isolated container is out of scope here: the runner reports a successful push. */
    @MockitoBean GitJobRunner runner;
    @MockitoBean GitConnectionChecker checker;
    @MockitoBean OpenPolicyAgentClient opa;
    @Autowired WebApplicationContext context;
    @Autowired DataStore store;
    private final JsonMapper json=JsonMapper.builder().build();
    private MockMvc mvc;

    @BeforeEach void setUp() {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(runner.run(anyString(),anyString(),anyString(),anyMap())).thenReturn(new GitJobRunner.Result(0,"OJCT_COMMIT=abc123\n"));
    }

    private MockHttpServletRequestBuilder asEditor(MockHttpServletRequestBuilder request) {
        return request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_openjcockpit-operator")))
            .contentType(MediaType.APPLICATION_JSON);
    }
    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(j->j.claim("preferred_username","root"))
            .authorities(new SimpleGrantedAuthority("ROLE_openjcockpit-admin"))).contentType(MediaType.APPLICATION_JSON);
    }
    private JsonNode admin(MockHttpServletRequestBuilder request,int status) throws Exception {
        String body=mvc.perform(asAdmin(request)).andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }
    private JsonNode call(MockHttpServletRequestBuilder request,int status) throws Exception {
        String body=mvc.perform(asEditor(request)).andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
        return body.isEmpty()?null:json.readTree(body);
    }
    private static String project(String name,String gitUrl,String type,String secret) {
        return project(name,gitUrl,type,secret,"cockpit");
    }
    private static String project(String name,String gitUrl,String type,String secret,String cockpitProjectId) {
        return "{\"name\":\""+name+"\",\"gitUrl\":\""+gitUrl+"\",\"credentialType\":\""+type+"\",\"gitUsername\":\"bot\","
            +(secret==null?"":"\"secret\":\""+secret+"\",")+"\"cockpitProjectId\":\""+cockpitProjectId+"\",\"workflowId\":\"wf\"}";
    }

    @Test void exposesProbesAndMetricsWithoutAuthentication() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk()).andExpect(content().string(containsString("jvm_")));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
    @Test void enforcesAuthenticationAndRoles() throws Exception {
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/projects").with(jwt())).andExpect(status().isOk());
        mvc.perform(post("/api/projects").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/internal").with(jwt())).andExpect(status().isForbidden());
    }
    @Test void validatesInput() throws Exception {
        call(post("/api/projects").content(project("","https://git.example.org/r.git","NONE",null)),400);
        call(post("/api/projects").content(project("n","http://git.example.org/r.git","NONE",null)),400);
        call(post("/api/projects").content(project("n","https://git.example.org/r.git","HTTPS_TOKEN",null)),400);
        call(put("/api/projects/missing").content(project("n","https://git.example.org/r.git","NONE",null)),400);
        String question="{\"id\":\"q\",\"text\":\"Q?\",\"required\":true,\"maxFollowUps\":1,\"completionCriteria\":\"c\"}";
        call(post("/api/templates").content("{\"name\":\"T\",\"kind\":\"OTHER\",\"questions\":["+question+"]}"),400);
        call(post("/api/templates").content("{\"name\":\"T\",\"kind\":\"PROJECT\",\"questions\":[]}"),400);
        call(post("/api/templates").content("{\"name\":\"T\",\"kind\":\"PROJECT\",\"questions\":["+question.replace("1,","9,")+"]}"),400);
        call(post("/api/spec-templates").content("{\"name\":\"S\",\"content\":\"no placeholders\"}"),400);
        call(post("/api/interviews/missing/answers").content("{\"answer\":\"x\"}"),400);
    }
    @Test void runsDiscoveryToDispatchFlow() throws Exception {
        int builtIn=0;
        for (JsonNode t:call(get("/api/templates"),200)) if (t.get("builtIn").asBoolean()) builtIn++;
        assertEquals(2,builtIn);
        assertEquals(1,call(get("/api/spec-templates"),200).size());
        String question="{\"id\":\"q\",\"text\":\"Q?\",\"required\":false,\"maxFollowUps\":0,\"completionCriteria\":\"c\"}";
        JsonNode template=call(post("/api/templates").content("{\"name\":\"Mini\",\"kind\":\"PROJECT\",\"questions\":["+question+"]}"),201);
        JsonNode specTemplate=call(post("/api/spec-templates").content(
            "{\"name\":\"Custom\",\"content\":\"# {{TITLE}}\\nID: {{SPEC_ID}}\\n{{ACCEPTANCE_CRITERIA}}\"}"),201);

        String created=mvc.perform(asEditor(post("/api/projects").content(project(" Demo ","https://git.example.org/team/demo.git","HTTPS_TOKEN","ghp_secret"))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertFalse(created.contains("ghp_secret"),"the secret is write-only");
        JsonNode project=json.readTree(created);
        String projectId=project.get("id").asText();
        assertEquals("Demo",project.get("name").asText());
        assertTrue(project.get("hasSecret").asBoolean());
        assertEquals("main",project.get("defaultBranch").asText());
        when(checker.check("https://git.example.org/team/demo.git","bot","ghp_secret"))
            .thenReturn(new GitConnectionChecker.Result(true,"OK","Connected"));
        JsonNode check=call(post("/api/projects/connection-check").content("{\"gitUrl\":\"https://git.example.org/team/demo.git\","
            +"\"credentialType\":\"HTTPS_TOKEN\",\"gitUsername\":\"bot\",\"projectId\":\""+projectId+"\"}"),200);
        assertTrue(check.get("reachable").asBoolean());
        assertEquals("OK",check.get("outcome").asText());
        call(post("/api/projects/connection-check").content("{\"gitUrl\":\"https://elsewhere.example.net/x.git\","
            +"\"credentialType\":\"HTTPS_TOKEN\",\"projectId\":\""+projectId+"\"}"),400);
        call(post("/api/projects/connection-check").content("{\"credentialType\":\"NONE\"}"),400);
        // Updating without a secret keeps the stored one.
        JsonNode updated=call(put("/api/projects/"+projectId).content(project("Demo","https://git.example.org/team/demo.git","HTTPS_TOKEN",null)),200);
        assertTrue(updated.get("hasSecret").asBoolean());
        assertTrue(call(get("/api/projects"),200).size()>=1);

        JsonNode interview=call(post("/api/interviews").content("{\"projectId\":\""+projectId+"\",\"templateId\":\""
            +template.get("id").asText()+"\"}"),201);
        String interviewId=interview.get("id").asText();
        call(post("/api/interviews/"+interviewId+"/generate"),400);
        JsonNode answered=call(post("/api/interviews/"+interviewId+"/answers").content("{\"answer\":\"Short\"}"),200);
        assertEquals("COMPLETE",answered.get("status").asText());
        assertEquals("COMPLETE",call(get("/api/interviews/"+interviewId),200).get("status").asText());
        assertEquals(1,call(get("/api/projects/"+projectId+"/interviews"),200).size());

        JsonNode specs=call(post("/api/interviews/"+interviewId+"/generate").content(
            "{\"specTemplateId\":\""+specTemplate.get("id").asText()+"\"}"),200);
        assertEquals(3,specs.size());
        specs.forEach(spec->assertEquals(interviewId,spec.get("interviewId").asText()));
        assertTrue(specs.get(0).get("body").asText().startsWith("# Define acceptance contract"));
        assertEquals(3,call(get("/api/projects/"+projectId+"/specs"),200).size());
        assertEquals(3,call(get("/api/projects/"+projectId+"/graph"),200).get("topologicalOrder").size());

        String first=specs.get(0).get("id").asText();
        JsonNode approved=call(post("/api/specs/"+first+"/approve"),200);
        assertEquals("APPROVED",approved.get("status").asText());
        assertEquals("spec/"+first,approved.get("branch").asText());
        verify(runner).run(anyString(),eq("alpine/git:2.49.1"),contains("git push"),argThat(env->
            "ghp_secret".equals(env.get("GIT_PASSWORD")) && ("spec/"+first).equals(env.get("OJCT_BRANCH"))));
        call(post("/api/specs/"+first+"/complete"),400);
        JsonNode proposal=call(get("/api/projects/"+projectId+"/queue/proposal"),200);
        assertEquals(first,proposal.get(0).get("specId").asText());

        call(post("/api/projects/"+projectId+"/queue/dispatch").content("{\"repositoryPublished\":false}"),400);
        // Dispatch is disabled in this configuration: the reservation stays UNKNOWN and is never retried.
        JsonNode outcome=call(post("/api/projects/"+projectId+"/queue/dispatch").content("{\"repositoryPublished\":true}"),200);
        assertTrue(outcome.get(0).get("result").asText().startsWith("UNKNOWN"));
        assertEquals(0,call(get("/api/projects/"+projectId+"/queue/proposal"),200).size());
        // After a human has reconciled the run in OpenJCockpit, the spec can be acknowledged as DONE.
        assertEquals(0,call(post("/api/projects/"+projectId+"/queue/reconcile"),200).size());
        store.setSpecStatus(first,"DISPATCHED");
        // Reconciling asks OpenJCockpit, which is disabled here: the call fails and the spec is left untouched.
        call(post("/api/projects/"+projectId+"/queue/reconcile"),409);
        assertEquals("DONE",call(post("/api/specs/"+first+"/complete"),200).get("status").asText());
    }
    @Test void findsTheRepositoryOfACockpitProjectAndKeepsItUnique() throws Exception {
        JsonNode created=call(post("/api/projects").content(project("Mapped","https://git.example.org/mapped.git","NONE",null,"cockpit-mapped")),201);
        JsonNode found=call(get("/api/projects").param("cockpitProjectId","cockpit-mapped"),200);
        assertEquals(1,found.size());
        assertEquals(created.get("id").asText(),found.get(0).get("id").asText());
        assertEquals(0,call(get("/api/projects").param("cockpitProjectId","unknown"),200).size());
        assertTrue(call(get("/api/projects"),200).size()>1);
        assertEquals("OpenJCockpit project cockpit-mapped already has a repository",
            call(post("/api/projects").content(project("Twin","https://git.example.org/twin.git","NONE",null,"cockpit-mapped")),409).get("error").asText());
        // Saving a repository again for its own Cockpit project is an update, not a duplicate.
        call(put("/api/projects/"+created.get("id").asText()).content(project("Mapped 2","https://git.example.org/mapped.git","NONE",null,"cockpit-mapped")),200);
    }
    @Test void letsAdministratorsChangeThePolicyAgent() throws Exception {
        JsonNode view=call(get("/api/settings"),200);
        assertFalse(view.get("editable").asBoolean());
        assertFalse(view.get("policy").get("enabled").asBoolean());
        assertTrue(admin(get("/api/settings"),200).get("editable").asBoolean());
        mvc.perform(get("/api/public/auth-config")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/public/auth-config").with(jwt())).andExpect(status().isNotFound());
        call(put("/api/settings/keycloak").content("{}"),403);
        String policy="{\"enabled\":true,\"url\":\"http://opa:8181\",\"decisionPath\":\"openjcontroltower/decision\",\"failOpen\":false}";
        Settings.Policy enabled=new Settings.Policy(true,"http://opa:8181","openjcontroltower/decision",false);
        when(opa.status(enabled)).thenReturn(new OpenPolicyAgentClient.Status(true,"Connected",List.of("decision.rego")));
        assertEquals("decision.rego",admin(post("/api/settings/policy/test").content(policy),200).get("policies").get(0).asText());
        call(put("/api/settings/policy").content(policy),403);
        try {
            admin(put("/api/settings/policy").content(policy),200);
            when(opa.decide(eq(enabled),anyMap())).thenReturn(new OpenPolicyAgentClient.Decision(false,List.of("Git host x is not allowed")));
            assertEquals("Denied by policy: Git host x is not allowed",
                call(post("/api/projects").content(project("Denied","https://x.example/r.git","NONE",null)),403).get("error").asText());
            verify(opa).decide(eq(enabled),argThat(input->input.get("action").equals("repository.save")));
        } finally {
            admin(put("/api/settings/policy").content(policy.replace("true,","false,")),200);
        }
        assertFalse(call(get("/api/settings"),200).get("policy").get("enabled").asBoolean());
    }
    @Test void generatesWithDefaultTemplateWhenNoBodyIsSent() throws Exception {
        JsonNode project=call(post("/api/projects").content(project("Second","https://git.example.org/second.git","NONE",null,"cockpit-second")),201);
        String projectTemplate=null;
        for (JsonNode t:call(get("/api/templates"),200)) if (t.get("name").asText().equals("Project discovery")) projectTemplate=t.get("id").asText();
        JsonNode interview=call(post("/api/interviews").content("{\"projectId\":\""+project.get("id").asText()
            +"\",\"templateId\":\""+projectTemplate+"\"}"),201);
        String id=interview.get("id").asText();
        String answer="{\"answer\":\"A detailed answer with measurable outcomes for every user group.\"}";
        for (int i=0;i<4;i++) call(post("/api/interviews/"+id+"/answers").content(answer),200);
        mvc.perform(asEditor(post("/api/interviews/"+id+"/generate"))).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].body",containsString("spec_id:"))).andExpect(jsonPath("$",hasSize(3)));
    }
    @Test void editsAndDeletesTemplatesWithGuards() throws Exception {
        String question="{\"id\":\"q\",\"text\":\"Q?\",\"required\":false,\"maxFollowUps\":0,\"completionCriteria\":\"c\"}";
        String body="{\"name\":\"Edited\",\"kind\":\"FEATURE\",\"questions\":["+question+"]}";
        JsonNode created=call(post("/api/templates").content(body.replace("Edited","Original")),201);
        assertFalse(created.get("builtIn").asBoolean());
        String id=created.get("id").asText();
        JsonNode updated=call(put("/api/templates/"+id).content(body),200);
        assertEquals("Edited",updated.get("name").asText());assertEquals("FEATURE",updated.get("kind").asText());
        call(put("/api/templates/"+id).content(body.replace("FEATURE","OTHER")),400);
        call(put("/api/templates/missing").content(body),404);
        mvc.perform(asEditor(delete("/api/templates/missing"))).andExpect(status().isNotFound());
        for (JsonNode t:call(get("/api/templates"),200)) if (t.get("builtIn").asBoolean()) {
            call(put("/api/templates/"+t.get("id").asText()).content(body),409);
            mvc.perform(asEditor(delete("/api/templates/"+t.get("id").asText()))).andExpect(status().isConflict());
        }
        JsonNode project=call(post("/api/projects").content(project("Tpl","https://git.example.org/tpl.git","NONE",null,"cockpit-tpl")),201);
        JsonNode featureTemplateInterview=call(post("/api/templates").content(body.replace("FEATURE","PROJECT")),201);
        String usedId=featureTemplateInterview.get("id").asText();
        JsonNode interview=call(post("/api/interviews").content("{\"projectId\":\""+project.get("id").asText()+"\",\"templateId\":\""+usedId+"\"}"),201);
        assertNotNull(interview.get("createdAt"));assertNotNull(interview.get("updatedAt"));
        call(put("/api/templates/"+usedId).content(body.replace("FEATURE","PROJECT")),409);
        mvc.perform(asEditor(delete("/api/templates/"+usedId))).andExpect(status().isConflict());
        mvc.perform(asEditor(delete("/api/templates/"+id))).andExpect(status().isNoContent());
        call(put("/api/templates/"+id).content(body),404);
    }
}

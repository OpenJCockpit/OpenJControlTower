package org.openjcontroltower;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openjcontroltower.infrastructure.DataStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Boots the full application on H2 with Flyway, Spring Security and the demo assistant. */
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:integration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "controltower.cockpit.enabled=false"})
class ControlTowerIntegrationTest {
    private static final Path WORKSPACE;
    static {
        try { WORKSPACE=Files.createTempDirectory("ct-workspace"); } catch (IOException e) { throw new IllegalStateException(e); }
    }
    @DynamicPropertySource static void workspace(DynamicPropertyRegistry registry) {
        registry.add("controltower.repositories-base",WORKSPACE::toString);
    }
    @Autowired WebApplicationContext context;
    @Autowired DataStore store;
    private final JsonMapper json=JsonMapper.builder().build();
    private MockMvc mvc;

    @BeforeEach void setUp() { mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    private MockHttpServletRequestBuilder asEditor(MockHttpServletRequestBuilder request) {
        return request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_controltower-editor")))
            .contentType(MediaType.APPLICATION_JSON);
    }
    private JsonNode call(MockHttpServletRequestBuilder request,int status) throws Exception {
        String body=mvc.perform(asEditor(request)).andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
        return body.isEmpty()?null:json.readTree(body);
    }
    private String newRepository() throws IOException {
        Path repo=Files.createDirectories(WORKSPACE.resolve("repo-"+UUID.randomUUID()).resolve(".git")).getParent();
        return repo.toString();
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
        call(post("/api/projects").content("{\"name\":\"\",\"rootPath\":\"/x\",\"cockpitProjectId\":\"c\",\"workflowId\":\"w\"}"),400);
        call(post("/api/projects").content("{\"name\":\"n\",\"rootPath\":\"/etc\",\"cockpitProjectId\":\"c\",\"workflowId\":\"w\"}"),400);
        String question="{\"id\":\"q\",\"text\":\"Q?\",\"required\":true,\"maxFollowUps\":1,\"completionCriteria\":\"c\"}";
        call(post("/api/templates").content("{\"name\":\"T\",\"kind\":\"OTHER\",\"questions\":["+question+"]}"),400);
        call(post("/api/templates").content("{\"name\":\"T\",\"kind\":\"PROJECT\",\"questions\":[]}"),400);
        call(post("/api/templates").content("{\"name\":\"T\",\"kind\":\"PROJECT\",\"questions\":["+question.replace("1,","9,")+"]}"),400);
        call(post("/api/spec-templates").content("{\"name\":\"S\",\"content\":\"no placeholders\"}"),400);
        call(post("/api/interviews/missing/answers").content("{\"answer\":\"x\"}"),400);
    }
    @Test void runsDiscoveryToDispatchFlow() throws Exception {
        assertEquals(2,call(get("/api/templates"),200).size());
        assertEquals(1,call(get("/api/spec-templates"),200).size());
        String question="{\"id\":\"q\",\"text\":\"Q?\",\"required\":false,\"maxFollowUps\":0,\"completionCriteria\":\"c\"}";
        JsonNode template=call(post("/api/templates").content("{\"name\":\"Mini\",\"kind\":\"PROJECT\",\"questions\":["+question+"]}"),201);
        JsonNode specTemplate=call(post("/api/spec-templates").content(
            "{\"name\":\"Custom\",\"content\":\"# {{TITLE}}\\nID: {{SPEC_ID}}\\n{{ACCEPTANCE_CRITERIA}}\"}"),201);

        JsonNode project=call(post("/api/projects").content("{\"name\":\" Demo \",\"rootPath\":\""+newRepository()
            +"\",\"cockpitProjectId\":\"cockpit\",\"workflowId\":\"wf\"}"),201);
        String projectId=project.get("id").asText();
        assertEquals("Demo",project.get("name").asText());
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
        assertTrue(specs.get(0).get("body").asText().startsWith("# Define acceptance contract"));
        assertEquals(3,call(get("/api/projects/"+projectId+"/specs"),200).size());
        assertEquals(3,call(get("/api/projects/"+projectId+"/graph"),200).get("topologicalOrder").size());

        String first=specs.get(0).get("id").asText();
        assertEquals("APPROVED",call(post("/api/specs/"+first+"/approve"),200).get("status").asText());
        call(post("/api/specs/"+first+"/complete"),400);
        JsonNode proposal=call(get("/api/projects/"+projectId+"/queue/proposal"),200);
        assertEquals(first,proposal.get(0).get("specId").asText());

        call(post("/api/projects/"+projectId+"/queue/dispatch").content("{\"repositoryPublished\":false}"),400);
        // Dispatch is disabled in this configuration: the reservation stays UNKNOWN and is never retried.
        JsonNode outcome=call(post("/api/projects/"+projectId+"/queue/dispatch").content("{\"repositoryPublished\":true}"),200);
        assertTrue(outcome.get(0).get("result").asText().startsWith("UNKNOWN"));
        assertEquals(0,call(get("/api/projects/"+projectId+"/queue/proposal"),200).size());
        // After a human has reconciled the run in OpenJCockpit, the spec can be acknowledged as DONE.
        store.setSpecStatus(first,"DISPATCHED");
        assertEquals("DONE",call(post("/api/specs/"+first+"/complete"),200).get("status").asText());
    }
    @Test void generatesWithDefaultTemplateWhenNoBodyIsSent() throws Exception {
        JsonNode project=call(post("/api/projects").content("{\"name\":\"Second\",\"rootPath\":\""+newRepository()
            +"\",\"cockpitProjectId\":\"c\",\"workflowId\":\"wf\"}"),201);
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
}

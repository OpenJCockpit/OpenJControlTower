package org.openjcontroltower.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.openjcontroltower.application.DispatchService;
import org.openjcontroltower.application.InterviewService;
import org.openjcontroltower.application.CurrentUser;
import org.openjcontroltower.application.ProjectService;
import org.openjcontroltower.application.SettingsService;
import org.openjcontroltower.application.SpecService;
import org.openjcontroltower.domain.Models.*;
import org.openjcontroltower.domain.Settings;
import org.openjcontroltower.infrastructure.DataStore;
import org.openjcontroltower.infrastructure.GitConnectionChecker;
import org.openjcontroltower.infrastructure.KeycloakProbe;
import org.openjcontroltower.infrastructure.OpenPolicyAgentClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final DataStore db;
    private final ProjectService projects;
    private final InterviewService interviews;
    private final SpecService specs;
    private final DispatchService dispatch;
    private final SettingsService settings;
    private final String adminRole;
    public ApiController(DataStore db,ProjectService projects,InterviewService interviews,SpecService specs,
        DispatchService dispatch,SettingsService settings,@Value("${CONTROLTOWER_ADMIN_ROLE:controltower-admin}") String adminRole) {
        this.db=db;this.projects=projects;this.interviews=interviews;this.specs=specs;this.dispatch=dispatch;
        this.settings=settings;this.adminRole=adminRole;
    }
    /** What the web application needs to sign in; public because it is read before sign-in. */
    public record AuthConfig(String url,String realm,String clientId) {}
    /** {@code editable}: whether the current user may change the settings (the admin role). */
    public record SettingsView(Settings.Keycloak keycloak,Settings.Policy policy,boolean editable) {}
    /** {@code secret} is write-only: it is never returned, and leaving it empty on update keeps the stored one. */
    public record ProjectRequest(@NotBlank String name,@NotBlank String gitUrl,String defaultBranch,
                                 @NotBlank String credentialType,String gitUsername,String secret,
                                 @NotBlank String cockpitProjectId,@NotBlank String workflowId) {
        ProjectService.Input input() {
            return new ProjectService.Input(name,gitUrl,defaultBranch,credentialType,gitUsername,secret,cockpitProjectId,workflowId);
        }
    }
    /** {@code projectId} is optional: when editing, it lets an empty secret fall back to the stored one. */
    public record ConnectionCheckRequest(@NotBlank String gitUrl,@NotBlank String credentialType,String gitUsername,
                                         String secret,String projectId) {}
    public record NewTemplate(@NotBlank String name,@NotBlank String kind,@NotNull List<Question> questions) {}
    public record NewInterview(@NotBlank String projectId,@NotBlank String templateId,String featureRequest) {}
    public record NewSpecTemplate(@NotBlank String name,@NotBlank String content) {}
    public record SpecGenerationRequest(String specTemplateId) {}
    public record NewAnswer(@NotBlank String answer) {}
    public record DispatchRequest(boolean repositoryPublished) {}

    @GetMapping("/public/auth-config") public AuthConfig authConfig() {
        Settings.Keycloak keycloak=settings.keycloak();
        return new AuthConfig(keycloak.url(),keycloak.realm(),keycloak.clientId());
    }
    @GetMapping("/settings") public SettingsView settings() {
        return new SettingsView(settings.keycloak(),settings.policy(),CurrentUser.get().hasRole(adminRole));
    }
    @PutMapping("/settings/keycloak") public Settings.Keycloak updateKeycloak(@RequestBody Settings.Keycloak input) {
        return settings.updateKeycloak(input);
    }
    @PostMapping("/settings/keycloak/test") public KeycloakProbe.Result testKeycloak(@RequestBody Settings.Keycloak input) {
        return settings.testKeycloak(input);
    }
    @PutMapping("/settings/policy") public Settings.Policy updatePolicy(@RequestBody Settings.Policy input) {
        return settings.updatePolicy(input);
    }
    @PostMapping("/settings/policy/test") public OpenPolicyAgentClient.Status testPolicy(@RequestBody Settings.Policy input) {
        return settings.testPolicy(input);
    }
    @GetMapping("/projects") public List<Project> projects() { return db.projects(); }
    @PostMapping("/projects") @ResponseStatus(HttpStatus.CREATED)
    public Project createProject(@Valid @RequestBody ProjectRequest input) { return projects.create(input.input()); }
    @PutMapping("/projects/{id}")
    public Project updateProject(@PathVariable String id,@Valid @RequestBody ProjectRequest input) { return projects.update(id,input.input()); }
    @PostMapping("/projects/connection-check")
    public GitConnectionChecker.Result checkConnection(@Valid @RequestBody ConnectionCheckRequest input) {
        return projects.checkConnection(input.projectId(),input.gitUrl(),input.credentialType(),input.gitUsername(),input.secret());
    }
    @GetMapping("/templates") public List<Template> templates() { return db.templates(); }
    @PostMapping("/templates") @ResponseStatus(HttpStatus.CREATED)
    public Template createTemplate(@Valid @RequestBody NewTemplate input) {
        if (!List.of("PROJECT","FEATURE").contains(input.kind())) throw new IllegalArgumentException("Kind must be PROJECT or FEATURE");
        if (input.questions().isEmpty() || input.questions().size()>30) throw new IllegalArgumentException("Expected 1-30 questions");
        if (input.questions().stream().anyMatch(q->q.id()==null || q.id().isBlank() || q.text()==null || q.text().isBlank()
            || q.maxFollowUps()<0 || q.maxFollowUps()>5)) throw new IllegalArgumentException("Invalid question");
        Template t=new Template(UUID.randomUUID().toString(),input.name(),input.kind(),input.questions());
        db.saveTemplate(t);return t;
    }
    @GetMapping("/spec-templates") public List<SpecTemplate> specTemplates() { return db.specTemplates(); }
    @PostMapping("/spec-templates") @ResponseStatus(HttpStatus.CREATED)
    public SpecTemplate createSpecTemplate(@Valid @RequestBody NewSpecTemplate input) {
        if (input.content().length()>20000 || !input.content().contains("{{SPEC_ID}}")
            || !input.content().contains("{{ACCEPTANCE_CRITERIA}}"))
            throw new IllegalArgumentException("Spec template requires {{SPEC_ID}} and {{ACCEPTANCE_CRITERIA}} and max 20KB");
        SpecTemplate template=new SpecTemplate(UUID.randomUUID().toString(),input.name(),input.content(),false);
        db.saveSpecTemplate(template);return template;
    }
    @GetMapping("/projects/{projectId}/interviews")
    public List<Interview> interviews(@PathVariable String projectId) { return db.interviews(projectId); }
    @PostMapping("/interviews") @ResponseStatus(HttpStatus.CREATED)
    public Interview start(@Valid @RequestBody NewInterview input) {
        return interviews.start(input.projectId(),input.templateId(),input.featureRequest());
    }
    @GetMapping("/interviews/{id}") public Interview interview(@PathVariable String id) { return db.interview(id); }
    @PostMapping("/interviews/{id}/answers") public Interview answer(@PathVariable String id,@Valid @RequestBody NewAnswer input) {
        return interviews.answer(id,input.answer());
    }
    @PostMapping("/interviews/{id}/generate")
    public List<Spec> generate(@PathVariable String id,@RequestBody(required=false) SpecGenerationRequest input) {
        return specs.generate(id,input==null?null:input.specTemplateId());
    }
    @GetMapping("/projects/{projectId}/specs") public List<Spec> specs(@PathVariable String projectId) { return db.specs(projectId); }
    @GetMapping("/projects/{projectId}/graph") public Graph graph(@PathVariable String projectId) { return specs.graph(projectId); }
    @PostMapping("/specs/{id}/approve") public Spec approve(@PathVariable String id) { return specs.approve(id); }
    @PostMapping("/specs/{id}/complete") public Spec complete(@PathVariable String id) { return specs.markDone(id); }
    @GetMapping("/projects/{projectId}/queue/proposal")
    public List<DispatchService.QueueCandidate> queue(@PathVariable String projectId) { return dispatch.candidates(projectId); }
    @PostMapping("/projects/{projectId}/queue/dispatch")
    public List<DispatchService.DispatchOutcome> enqueue(@PathVariable String projectId,@RequestBody DispatchRequest input) {
        return dispatch.dispatch(projectId,input.repositoryPublished());
    }
}

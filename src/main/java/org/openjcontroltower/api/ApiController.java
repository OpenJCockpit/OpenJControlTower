package org.openjcontroltower.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.openjcontroltower.application.DispatchService;
import org.openjcontroltower.application.InterviewService;
import org.openjcontroltower.application.SpecService;
import org.openjcontroltower.domain.Models.*;
import org.openjcontroltower.infrastructure.DataStore;
import org.openjcontroltower.infrastructure.FileSpecPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final DataStore db;
    private final FileSpecPublisher files;
    private final InterviewService interviews;
    private final SpecService specs;
    private final DispatchService dispatch;
    public ApiController(DataStore db,FileSpecPublisher files,InterviewService interviews,
        SpecService specs,DispatchService dispatch) {
        this.db=db;this.files=files;this.interviews=interviews;this.specs=specs;this.dispatch=dispatch;
    }
    public record NewProject(@NotBlank String name,@NotBlank String rootPath,
                             @NotBlank String cockpitProjectId,@NotBlank String workflowId) {}
    public record NewTemplate(@NotBlank String name,@NotBlank String kind,@NotNull List<Question> questions) {}
    public record NewInterview(@NotBlank String projectId,@NotBlank String templateId,String featureRequest) {}
    public record NewSpecTemplate(@NotBlank String name,@NotBlank String content) {}
    public record SpecGenerationRequest(String specTemplateId) {}
    public record NewAnswer(@NotBlank String answer) {}
    public record DispatchRequest(boolean repositoryPublished) {}

    @GetMapping("/projects") public List<Project> projects() { return db.projects(); }
    @PostMapping("/projects") @ResponseStatus(HttpStatus.CREATED)
    public Project createProject(@Valid @RequestBody NewProject input) {
        String path=files.validateRoot(input.rootPath()).toString();
        Project project=new Project(UUID.randomUUID().toString(),input.name().strip(),path,
            input.cockpitProjectId().strip(),input.workflowId().strip());
        db.saveProject(project);return project;
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

package org.openjcontroltower.application;

import org.openjcontroltower.domain.DagPlanner;
import org.openjcontroltower.domain.SpecNaming;
import org.openjcontroltower.domain.Models.*;
import org.openjcontroltower.infrastructure.DataStore;
import org.openjcontroltower.infrastructure.GitSpecPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class SpecService {
    private final DataStore store;
    private final AiAssistant ai;
    private final GitSpecPublisher publisher;
    private final PolicyGuard policies;
    private final DagPlanner graph = new DagPlanner();
    private static final Pattern CRITERION=Pattern.compile("(?m)^\\s*[-*] \\[[ xX]\\] (.+)$");
    public SpecService(DataStore store,AiAssistant ai,GitSpecPublisher publisher,PolicyGuard policies) {
        this.store=store;this.ai=ai;this.publisher=publisher;this.policies=policies;
    }
    @Transactional
    public List<Spec> generate(String interviewId,String specTemplateId) {
        Interview interview=store.interview(interviewId);
        if (!interview.status().equals("COMPLETE")) throw new IllegalArgumentException("Interview must be completed first");
        Project project=store.project(interview.projectId());
        String context=store.interviews(project.id()).stream().filter(i->i.kind().equals("PROJECT"))
            .map(i->i.answers().toString()).reduce("", (a,b)->a+"\n"+b);
        List<Spec> current=store.specs(project.id());
        String knownSpecs=current.stream().map(s->s.id()+" ("+s.status()+") "+s.title())
            .reduce("",(a,b)->a+"\n"+b);
        AiAssistant.Plan plan=ai.decompose(context+"\nKNOWN SPEC IDS:"+knownSpecs,interview.answers());
        if (plan.specs()==null || plan.specs().isEmpty() || plan.specs().size()>6)
            throw new IllegalArgumentException("Expected 1-6 narrow specs");
        SpecTemplate template = (specTemplateId == null || specTemplateId.isBlank())
            ? store.specTemplates().stream().filter(SpecTemplate::defaultTemplate).findFirst()
                .orElseThrow(()->new IllegalStateException("No default spec template configured"))
            : store.specTemplate(specTemplateId);
        List<String> ids=plan.specs().stream().map(p->SpecNaming.newId()).toList();
        List<Spec> result=new ArrayList<>();
        for (int i=0;i<plan.specs().size();i++) {
            Proposal p=plan.specs().get(i);
            if (p.title()==null || p.title().isBlank() || p.summary()==null || p.summary().isBlank()
                || p.acceptanceCriteria()==null || p.acceptanceCriteria().size()<2)
                throw new IllegalArgumentException("Missing title, summary or testable criteria");
            List<String> dependencies=new ArrayList<>();
            if (p.dependsOnIndexes()!=null) for (Integer predecessor:p.dependsOnIndexes()) {
                if (predecessor==null || predecessor<0 || predecessor>=i)
                    throw new IllegalArgumentException("Dependency must refer to a previous proposal");
                dependencies.add(ids.get(predecessor));
            }
            if (p.existingDependencyIds()!=null) for (String existing:p.existingDependencyIds()) {
                if (current.stream().noneMatch(s->s.id().equals(existing)))
                    throw new IllegalArgumentException("Model referenced an unknown existing spec: "+existing);
                dependencies.add(existing);
            }
            String slug=SpecNaming.slug(p.title());
            String path=String.format("%03d-%s/spec.md",current.size()+i+1,slug);
            String body=formatMarkdown(template.content(),ids.get(i),project,p,dependencies);
            result.add(new Spec(ids.get(i),project.id(),p.title(),slug,"DRAFT",body,List.copyOf(dependencies),path,null));
        }
        graph.graph(concat(current,result));
        String author=CurrentUser.get().name();
        result.forEach(spec->store.saveSpec(spec,author));
        store.saveInterview(new Interview(interview.id(),interview.projectId(),interview.templateId(),interview.kind(),
            interview.title(),"GENERATED",interview.answers(),interview.questionIndex(),interview.followUps(),""));
        return result;
    }
    private List<Spec> concat(List<Spec> a,List<Spec> b) {
        List<Spec> all=new ArrayList<>(a);all.addAll(b);return all;
    }
    private String formatMarkdown(String template,String id,Project project,Proposal p,List<String> deps) {
        String criteria=String.join("\n",p.acceptanceCriteria().stream().map(a->"- [ ] " + a.replace("\n"," ")).toList());
        return template.replace("{{SPEC_ID}}",id)
            .replace("{{WORKFLOW_ID}}",project.workflowId())
            .replace("{{TITLE}}",p.title().replace("\n"," "))
            .replace("{{SUMMARY}}",p.summary())
            .replace("{{DEPENDENCIES}}",deps.toString())
            .replace("{{ACCEPTANCE_CRITERIA}}",criteria);
    }
    public Graph graph(String projectId) { return graph.graph(store.specs(projectId)); }
    public Spec approve(String specId) {
        Spec s=store.spec(specId);
        if (!s.status().equals("DRAFT")) throw new IllegalArgumentException("Only DRAFT can be approved");
        graph.graph(store.specs(s.projectId()));
        policies.check("spec.approve",PolicyGuard.resource("id",s.id(),"projectId",s.projectId(),"title",s.title(),
            "body",s.body(),"dependsOn",s.dependsOn(),"createdBy",store.specCreator(specId),
            "acceptanceCriteria",CRITERION.matcher(s.body()).results().map(m->m.group(1).strip()).toList()));
        Project project=store.project(s.projectId());
        GitSpecPublisher.Published published=publisher.publish(project,store.encryptedSecret(project.id()),s);
        store.setSpecPublished(specId,"APPROVED",published.branch(),published.commit());
        return store.spec(specId);
    }
    public Spec markDone(String specId) {
        Spec s=store.spec(specId);
        if (!s.status().equals("DISPATCHED")) throw new IllegalArgumentException("Only DISPATCHED can become DONE");
        // Manual acknowledgement in MVP; production must reconcile with OpenJCockpit before marking DONE.
        store.setSpecStatus(specId,"DONE");
        return store.spec(specId);
    }
}

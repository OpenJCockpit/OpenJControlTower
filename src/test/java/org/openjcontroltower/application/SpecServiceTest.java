package org.openjcontroltower.application;

import org.junit.jupiter.api.Test;
import org.openjcontroltower.domain.Models.*;
import org.openjcontroltower.infrastructure.DataStore;
import org.openjcontroltower.infrastructure.GitSpecPublisher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SpecServiceTest {
    private final DataStore store=mock(DataStore.class);
    private final AiAssistant ai=mock(AiAssistant.class);
    private final GitSpecPublisher publisher=mock(GitSpecPublisher.class);
    private final PolicyGuard policies=mock(PolicyGuard.class);
    private final SpecService service=new SpecService(store,ai,publisher,policies);
    private final Project project=new Project("p","Example","https://git.example.org/team/repo.git","main","NONE",null,false,"c","wf-1");
    private final SpecTemplate defaultTemplate=new SpecTemplate("st","Default",
        "{{SPEC_ID}}|{{WORKFLOW_ID}}|{{TITLE}}|{{SUMMARY}}|{{DEPENDENCIES}}|{{ACCEPTANCE_CRITERIA}}",true);
    private final Interview complete=new Interview("i","p","t","PROJECT","Project discovery","COMPLETE",
        List.of(new Answer("goal","Goal?","Ship it")),4,0,"");

    private static Proposal proposal(String title,List<Integer> deps,List<String> existing) {
        return new Proposal(title,"Summary of "+title,List.of("First criterion","Second\ncriterion"),deps,existing);
    }
    private void givenCompleteInterview(List<Spec> existing) {
        when(store.interview("i")).thenReturn(complete);
        when(store.project("p")).thenReturn(project);
        when(store.interviews("p")).thenReturn(List.of(complete,
            new Interview("f","p","t2","FEATURE","Feature","ACTIVE",List.of(),0,0,"Q")));
        when(store.specs("p")).thenReturn(existing);
        when(store.specTemplates()).thenReturn(List.of(new SpecTemplate("other","Other","x",false),defaultTemplate));
    }
    private static Spec spec(String id,String status,String... deps) {
        return new Spec(id,"p",id,"slug",status,"body",List.of(deps),"001-x/spec.md",null);
    }

    @Test void generatesFormattedSpecsWithDependenciesAndMarksInterviewGenerated() {
        givenCompleteInterview(List.of(spec("SPEC-old","DONE")));
        when(ai.decompose(contains("KNOWN SPEC IDS:\nSPEC-old (DONE) SPEC-old"),eq(complete.answers()))).thenReturn(new AiAssistant.Plan(List.of(
            proposal("First step",null,List.of("SPEC-old")),proposal("Second step",List.of(0),null))));
        List<Spec> result=service.generate("i",null);
        assertEquals(2,result.size());
        assertEquals("002-first-step/spec.md",result.get(0).specPath());
        assertEquals("003-second-step/spec.md",result.get(1).specPath());
        assertEquals(List.of("SPEC-old"),result.get(0).dependsOn());
        assertEquals(List.of(result.get(0).id()),result.get(1).dependsOn());
        assertTrue(result.get(0).body().startsWith(result.get(0).id()+"|wf-1|First step|Summary of First step|[SPEC-old]|- [ ] First criterion\n- [ ] Second criterion"));
        assertEquals("DRAFT",result.get(0).status());
        verify(store,times(2)).saveSpec(any(),any());
        verify(store).saveInterview(argThat(i->i.status().equals("GENERATED") && i.currentQuestion().isEmpty()));
    }
    @Test void usesExplicitSpecTemplate() {
        givenCompleteInterview(List.of());
        when(store.specTemplate("custom")).thenReturn(new SpecTemplate("custom","Custom","ID={{SPEC_ID}} {{ACCEPTANCE_CRITERIA}}",false));
        when(ai.decompose(anyString(),anyList())).thenReturn(new AiAssistant.Plan(List.of(proposal("Only",List.of(),List.of()))));
        assertTrue(service.generate("i","custom").getFirst().body().startsWith("ID=SPEC-"));
        // A blank template id falls back to the default template.
        assertTrue(service.generate("i"," ").getFirst().body().contains("|wf-1|"));
    }
    @Test void rejectsIncompleteInterviewAndInvalidPlans() {
        when(store.interview("active")).thenReturn(new Interview("active","p","t","PROJECT","x","ACTIVE",List.of(),0,0,"Q"));
        assertThrows(IllegalArgumentException.class,()->service.generate("active",null));
        givenCompleteInterview(List.of());
        when(ai.decompose(anyString(),anyList())).thenReturn(new AiAssistant.Plan(null));
        assertThrows(IllegalArgumentException.class,()->service.generate("i",null));
        when(ai.decompose(anyString(),anyList())).thenReturn(new AiAssistant.Plan(List.of()));
        assertThrows(IllegalArgumentException.class,()->service.generate("i",null));
        List<Proposal> seven=new ArrayList<>();
        for (int i=0;i<7;i++) seven.add(proposal("P"+i,null,null));
        when(ai.decompose(anyString(),anyList())).thenReturn(new AiAssistant.Plan(seven));
        assertThrows(IllegalArgumentException.class,()->service.generate("i",null));
        verify(store,never()).saveSpec(any(),any());
    }
    @Test void requiresADefaultTemplate() {
        givenCompleteInterview(List.of());
        when(store.specTemplates()).thenReturn(List.of());
        when(ai.decompose(anyString(),anyList())).thenReturn(new AiAssistant.Plan(List.of(proposal("Only",null,null))));
        assertThrows(IllegalStateException.class,()->service.generate("i",null));
    }
    @Test void rejectsIncompleteProposals() {
        givenCompleteInterview(List.of());
        for (Proposal invalid:List.of(
            new Proposal(null,"s",List.of("a","b"),null,null),new Proposal(" ","s",List.of("a","b"),null,null),
            new Proposal("t",null,List.of("a","b"),null,null),new Proposal("t"," ",List.of("a","b"),null,null),
            new Proposal("t","s",null,null,null),new Proposal("t","s",List.of("only one"),null,null))) {
            when(ai.decompose(anyString(),anyList())).thenReturn(new AiAssistant.Plan(List.of(invalid)));
            assertThrows(IllegalArgumentException.class,()->service.generate("i",null));
        }
    }
    @Test void rejectsInvalidDependencies() {
        givenCompleteInterview(List.of());
        for (List<Integer> deps:List.of(Arrays.asList((Integer)null),List.of(-1),List.of(1))) {
            when(ai.decompose(anyString(),anyList())).thenReturn(new AiAssistant.Plan(List.of(
                proposal("A",null,null),proposal("B",deps,null))));
            assertThrows(IllegalArgumentException.class,()->service.generate("i",null));
        }
        when(ai.decompose(anyString(),anyList())).thenReturn(new AiAssistant.Plan(List.of(proposal("A",null,List.of("SPEC-ghost")))));
        assertThrows(IllegalArgumentException.class,()->service.generate("i",null));
        // Combined graph validation: repeated dependency.
        when(ai.decompose(anyString(),anyList())).thenReturn(new AiAssistant.Plan(List.of(
            proposal("A",null,null),proposal("B",List.of(0,0),null))));
        assertThrows(IllegalArgumentException.class,()->service.generate("i",null));
        verify(store,never()).saveSpec(any(),any());
    }
    @Test void computesGraphForProject() {
        when(store.specs("p")).thenReturn(List.of(spec("A","DONE"),spec("B","APPROVED","A")));
        Graph graph=service.graph("p");
        assertEquals(List.of("A","B"),graph.topologicalOrder());
        assertTrue(graph.nodes().get(1).ready());
    }
    @Test void approvesDraftByPushingItsOwnBranch() {
        Spec draft=spec("A","DRAFT");
        when(store.spec("A")).thenReturn(draft,spec("A","APPROVED"));
        when(store.specs("p")).thenReturn(List.of(draft));
        when(store.project("p")).thenReturn(project);
        when(store.encryptedSecret("p")).thenReturn("cipher");
        when(publisher.publish(project,"cipher",draft)).thenReturn(new GitSpecPublisher.Published("spec/A","abc123"));
        assertEquals("APPROVED",service.approve("A").status());
        verify(store).setSpecPublished("A","APPROVED","spec/A","abc123");
    }
    @Test void asksThePoliciesBeforePushingAndPassesTheAcceptanceCriteria() {
        Spec draft=new Spec("A","p","Title","slug","DRAFT","# A\n- [ ] First\n  * [x] Second \n- not a criterion",List.of("Z"),"001-x/spec.md",null);
        when(store.spec("A")).thenReturn(draft);
        when(store.specs("p")).thenReturn(List.of(spec("Z","DONE"),draft));
        when(store.specCreator("A")).thenReturn("alice");
        doThrow(new PolicyGuard.PolicyDeniedException("Denied by policy: four eyes")).when(policies).check(eq("spec.approve"),anyMap());
        assertThrows(PolicyGuard.PolicyDeniedException.class,()->service.approve("A"));
        verify(policies).check("spec.approve",PolicyGuard.resource("id","A","projectId","p","title","Title","body",draft.body(),
            "dependsOn",List.of("Z"),"createdBy","alice","acceptanceCriteria",List.of("First","Second")));
        verifyNoInteractions(publisher);
    }
    @Test void rejectsApprovingNonDraft() {
        when(store.spec("A")).thenReturn(spec("A","APPROVED"));
        assertThrows(IllegalArgumentException.class,()->service.approve("A"));
        verifyNoInteractions(publisher);
    }
    @Test void marksOnlyDispatchedSpecsDone() {
        when(store.spec("A")).thenReturn(spec("A","DISPATCHED"),spec("A","DONE"));
        assertEquals("DONE",service.markDone("A").status());
        verify(store).setSpecStatus("A","DONE");
        when(store.spec("B")).thenReturn(spec("B","APPROVED"));
        assertThrows(IllegalArgumentException.class,()->service.markDone("B"));
    }
}

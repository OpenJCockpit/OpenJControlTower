package org.openjcontroltower.application;

import org.junit.jupiter.api.Test;
import org.openjcontroltower.domain.Models.*;
import org.openjcontroltower.infrastructure.DataStore;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InterviewServiceTest {
    private final DataStore store=mock(DataStore.class);
    private final AiAssistant ai=mock(AiAssistant.class);
    private final InterviewService service=new InterviewService(store,ai);
    private final Template feature=new Template("t","Feature","FEATURE",List.of(new Question("goal","What goal?",true,1,"Specific goal")));
    private final Template project=new Template("pt","Project","PROJECT",List.of(
        new Question("goal","Goal?",true,0,"Goal"),new Question("scope","Scope?",true,2,"Scope")));
    @Test void startsFeatureInterviewWithFeatureRequestContext() {
        when(store.template("t")).thenReturn(feature);
        when(store.interviews("p")).thenReturn(List.of(new Interview("p0","p","pTemplate","PROJECT","Initial project","COMPLETE",List.of(),1,0,"")));
        Interview result=service.start("p","t","Add a login feature");
        assertEquals("FEATURE",result.kind());assertEquals("ACTIVE",result.status());
        assertEquals("feature-request",result.answers().getFirst().questionId());
        verify(store).project("p");verify(store).saveInterview(result);
    }
    @Test void truncatesLongFeatureTitlesAndAcceptsGeneratedProjectInterview() {
        when(store.template("t")).thenReturn(feature);
        when(store.interviews("p")).thenReturn(List.of(
            new Interview("f0","p","t","FEATURE","Old feature","COMPLETE",List.of(),1,0,""),
            new Interview("p0","p","pt","PROJECT","Initial","ACTIVE",List.of(),0,0,"Q"),
            new Interview("p1","p","pt","PROJECT","Initial","GENERATED",List.of(),2,0,"")));
        assertEquals(140,service.start("p","t","x".repeat(300)).title().length());
    }
    @Test void startsProjectInterviewWithFirstQuestion() {
        when(store.template("pt")).thenReturn(project);
        Interview result=service.start("p","pt"," ");
        assertEquals("Project discovery",result.title());
        assertEquals("Goal?",result.currentQuestion());
        assertTrue(result.answers().isEmpty());
    }
    @Test void asksOneFollowUpThenCompletes() {
        when(store.template("t")).thenReturn(feature);
        Interview base=new Interview("i","p","t","FEATURE","login","ACTIVE",List.of(),0,0,"What goal?");
        when(store.interview("i")).thenReturn(base);
        when(ai.assess(any(),anyString(),anyList())).thenReturn(new AiAssistant.Evaluation(false,"Can you be precise?"));
        Interview followUp=service.answer("i","Login");
        assertEquals("ACTIVE",followUp.status());assertEquals(1,followUp.followUps());
        assertEquals("Can you be precise?",followUp.currentQuestion());
        when(store.interview("i")).thenReturn(followUp);
        when(ai.assess(any(),anyString(),anyList())).thenReturn(new AiAssistant.Evaluation(true,""));
        Interview done=service.answer("i","Users sign in with corporate OIDC tokens.");
        assertEquals("COMPLETE",done.status());assertEquals(2,done.answers().size());
        assertEquals("",done.currentQuestion());
    }
    @Test void advancesWhenFollowUpBudgetIsExhausted() {
        when(store.template("pt")).thenReturn(project);
        when(store.interview("i")).thenReturn(new Interview("i","p","pt","PROJECT","x","ACTIVE",List.of(),0,0,"Goal?"));
        when(ai.assess(any(),anyString(),anyList())).thenReturn(new AiAssistant.Evaluation(false,"More?"));
        Interview next=service.answer("i","  short  ");
        assertEquals(1,next.questionIndex());assertEquals("Scope?",next.currentQuestion());assertEquals(0,next.followUps());
        assertEquals("short",next.answers().getFirst().answer());
    }
    @Test void substitutesDefaultFollowUpWhenAssistantGivesNone() {
        when(store.template("pt")).thenReturn(project);
        when(store.interview("i")).thenReturn(new Interview("i","p","pt","PROJECT","x","ACTIVE",List.of(),1,0,"Scope?"));
        when(ai.assess(any(),anyString(),anyList())).thenReturn(new AiAssistant.Evaluation(false,null),new AiAssistant.Evaluation(false," "));
        assertEquals("Can you give a concrete example?",service.answer("i","a").currentQuestion());
        assertEquals("Can you give a concrete example?",service.answer("i","a").currentQuestion());
    }
    @Test void rejectsInvalidStartOrAnswers() {
        when(store.template("t")).thenReturn(feature);
        assertThrows(IllegalArgumentException.class,()->service.start("p","t",null));
        assertThrows(IllegalArgumentException.class,()->service.answer("i"," "));
        assertThrows(IllegalArgumentException.class,()->service.answer("i",null));
    }
    @Test void rejectsFeatureBeforeProjectDiscoveryAndEmptyTemplates() {
        when(store.template("t")).thenReturn(feature);
        when(store.interviews("p")).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,()->service.start("p","t","feature"));
        when(store.template("empty")).thenReturn(new Template("empty","Empty","PROJECT",List.of()));
        assertThrows(IllegalArgumentException.class,()->service.start("p","empty",null));
    }
    @Test void rejectsAnswersToInactiveInterview() {
        when(store.interview("i")).thenReturn(new Interview("i","p","t","FEATURE","x","COMPLETE",List.of(),1,0,""));
        assertThrows(IllegalArgumentException.class,()->service.answer("i","answer"));
    }
}

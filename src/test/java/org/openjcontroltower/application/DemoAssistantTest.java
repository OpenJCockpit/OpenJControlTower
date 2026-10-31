package org.openjcontroltower.application;

import org.junit.jupiter.api.Test;
import org.openjcontroltower.domain.Models.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DemoAssistantTest {
    private final DemoAssistant assistant=new DemoAssistant();
    @Test void shortAnswersNeedDetailUnlessOptional() {
        Question required=new Question("goal","Why?",true,2,"Detailed answer");
        assertFalse(assistant.assess(required,"No",List.of()).adequate());
        assertTrue(assistant.assess(required,"An acceptance criterion with numbers and user group.",List.of()).adequate());
        assertTrue(assistant.assess(new Question("opt","Why?",false,1,""),"No",List.of()).adequate());
    }
    @Test void producesThreeOrderedSmallDemoProposals() {
        AiAssistant.Plan result=assistant.decompose("context",List.of(new Answer("goal","Project goal","The goal")));
        assertEquals(3,result.specs().size());
        assertEquals(List.of(0),result.specs().get(1).dependsOnIndexes());
        assertEquals(List.of(1),result.specs().get(2).dependsOnIndexes());
    }
    @Test void usesFeatureRequestAndBoundsTitleLength() {
        String longRequest="r".repeat(200);
        AiAssistant.Plan result=assistant.decompose("context",List.of(new Answer("feature-request","New feature request",longRequest)));
        assertEquals("Define acceptance contract for "+"r".repeat(140),result.specs().getFirst().title());
        assertTrue(result.specs().getFirst().summary().endsWith(longRequest));
    }
    @Test void fallsBackToProjectFoundation() {
        assertTrue(assistant.decompose("context",List.of()).specs().getFirst().title().endsWith("Project foundation"));
    }
}

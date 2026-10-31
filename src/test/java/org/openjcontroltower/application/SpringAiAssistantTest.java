package org.openjcontroltower.application;

import org.junit.jupiter.api.Test;
import org.openjcontroltower.domain.Models.*;
import org.springframework.ai.chat.client.ChatClient;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SpringAiAssistantTest {
    private final ChatClient chat=mock(ChatClient.class,RETURNS_DEEP_STUBS);
    private final SpringAiAssistant assistant;
    private final Question question=new Question("goal","Goal?",true,1,"Measurable");
    SpringAiAssistantTest() {
        ChatClient.Builder builder=mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chat);
        assistant=new SpringAiAssistant(builder);
    }
    @Test void returnsStructuredAssessment() {
        AiAssistant.Evaluation expected=new AiAssistant.Evaluation(false,"Which users?");
        when(chat.prompt().system(anyString()).user(contains("Criterion: Measurable")).call().entity(AiAssistant.Evaluation.class)).thenReturn(expected);
        assertEquals(expected,assistant.assess(question,"Something",List.of()));
    }
    @Test void failsWhenModelReturnsNoAssessment() {
        when(chat.prompt().system(anyString()).user(anyString()).call().entity(AiAssistant.Evaluation.class)).thenReturn(null);
        assertThrows(IllegalStateException.class,()->assistant.assess(question,"Something",List.of()));
    }
    @Test void returnsStructuredPlan() {
        AiAssistant.Plan expected=new AiAssistant.Plan(List.of(new Proposal("t","s",List.of("a","b"),List.of(),List.of())));
        when(chat.prompt().system(anyString()).user(contains("PROJECT CONTEXT: ctx")).call().entity(AiAssistant.Plan.class)).thenReturn(expected);
        assertEquals(expected,assistant.decompose("ctx",List.of()));
    }
    @Test void failsWhenModelReturnsNoPlan() {
        when(chat.prompt().system(anyString()).user(anyString()).call().entity(AiAssistant.Plan.class)).thenReturn(null);
        assertThrows(IllegalStateException.class,()->assistant.decompose("ctx",List.of()));
    }
}

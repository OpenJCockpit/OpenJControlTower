package org.openjcontroltower.application;

import org.openjcontroltower.domain.Models.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.List;

/** Uses Spring AI structured output. No model is allowed to write files or enqueue items. */
@Component
@ConditionalOnProperty(prefix="controltower.ai",name="enabled",havingValue="true")
public class SpringAiAssistant implements AiAssistant {
    private final ChatClient chat;
    public SpringAiAssistant(ChatClient.Builder builder) { this.chat = builder.build(); }
    @Override public Evaluation assess(Question q,String answer,List<Answer> history) {
        Evaluation result = chat.prompt().system("""
            You are a precise product interviewer. Determine whether an answer satisfies the stated
            completion criterion; never invent facts. Ask exactly one crisp follow-up in English if needed.
            Be conservative. Return only the requested structured response.
            """).user("Question: " + q.text() + "\nCriterion: " + q.completionCriteria()
                + "\nAnswer: " + answer + "\nConversation so far: " + history)
            .call().entity(Evaluation.class);
        if (result == null) throw new IllegalStateException("AI returned no assessment");
        return result;
    }
    @Override public Plan decompose(String context,List<Answer> answers) {
        Plan result = chat.prompt().system("""
            You are a senior business analyst. Decompose requirements into 1-6 SMALL,
            independently testable, implementation-ready changes. Each should have one narrow outcome,
            clear scope and at least two verifiable acceptance criteria. Dependency indexes refer to
            earlier items in your returned list, zero based. ExistingDependencyIds may refer to
            previously known specs in project context only; never invent IDs. Do not invent project facts. If unknown,
            state it explicitly in the summary. Never put secrets or tokens into output.
            Return structured specs, no markdown wrapping.
            """).user("PROJECT CONTEXT: " + context + "\nINTERVIEW: " + answers)
            .call().entity(Plan.class);
        if (result == null) throw new IllegalStateException("AI returned no plan");
        return result;
    }
}

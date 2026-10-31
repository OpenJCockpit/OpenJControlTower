package org.openjcontroltower.application;

import org.openjcontroltower.domain.Models.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.*;

/** Transparent, deterministic demonstration mode. Not an AI replacement. */
@Component
@ConditionalOnProperty(prefix="controltower.ai",name="enabled",havingValue="false",matchIfMissing=true)
public class DemoAssistant implements AiAssistant {
    @Override public Evaluation assess(Question question, String answer, List<Answer> history) {
        boolean adequate = !question.required() || answer.trim().length() >= 25;
        return new Evaluation(adequate, "Can you make this more concrete with an example, a measurable result and the boundaries of the scope?");
    }
    @Override public Plan decompose(String context, List<Answer> answers) {
        String feature = answers.stream().filter(a->a.questionId().equals("feature-request"))
            .map(Answer::answer).findFirst().orElse("Project foundation");
        String goal = answers.stream().filter(a->a.questionId().equals("goal"))
            .map(Answer::answer).findFirst().orElse(feature);
        String shortGoal = goal.length() > 140 ? goal.substring(0, 140) : goal;
        List<Proposal> proposals = List.of(
            new Proposal("Define acceptance contract for " + shortGoal,
                "Document the smallest verifiable requirements and API boundaries for: " + goal,
                List.of("Acceptance criteria are independently testable", "Scope exclusions are documented"),List.of(),List.of()),
            new Proposal("Implement minimum increment for " + shortGoal,
                "Implement one end-to-end vertical slice in the configured repository.",
                List.of("An end-to-end happy-path test passes", "No unrelated code paths are changed"),List.of(0),List.of()),
            new Proposal("Verify quality for " + shortGoal,
                "Add failure-path tests and review quality and security implications.",
                List.of("Negative-path tests pass", "Documentation describes the implemented behavior"),List.of(1),List.of()));
        return new Plan(proposals);
    }
}

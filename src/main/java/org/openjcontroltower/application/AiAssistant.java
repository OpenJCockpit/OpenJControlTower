package org.openjcontroltower.application;

import org.openjcontroltower.domain.Models.*;
import java.util.List;

/** AI is advisory: the caller performs all workflow and integrity checks. */
public interface AiAssistant {
    record Evaluation(boolean adequate, String followUpQuestion) {}
    record Plan(List<Proposal> specs) {}
    Evaluation assess(Question question, String answer, List<Answer> history);
    Plan decompose(String projectContext, List<Answer> answers);
}

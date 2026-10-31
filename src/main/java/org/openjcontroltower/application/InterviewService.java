package org.openjcontroltower.application;

import org.openjcontroltower.domain.Models.*;
import org.openjcontroltower.infrastructure.DataStore;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class InterviewService {
    private final DataStore store;
    private final AiAssistant ai;
    public InterviewService(DataStore store, AiAssistant ai) { this.store=store; this.ai=ai; }

    public Interview start(String projectId, String templateId, String featureRequest) {
        store.project(projectId);
        Template template = store.template(templateId);
        if (template.questions().isEmpty()) throw new IllegalArgumentException("Template must contain questions");
        boolean feature = featureRequest != null && !featureRequest.isBlank();
        if (feature && store.interviews(projectId).stream()
            .noneMatch(i->i.kind().equals("PROJECT") &&
                (i.status().equals("COMPLETE") || i.status().equals("GENERATED"))))
            throw new IllegalArgumentException("Complete an initial project interview before feature discovery");
        if (!template.kind().equals(feature ? "FEATURE" : "PROJECT"))
            throw new IllegalArgumentException("Interview kind does not match template");
        List<Answer> initial = feature ? List.of(new Answer("feature-request", "New feature request", featureRequest)) : List.of();
        Interview interview = new Interview(UUID.randomUUID().toString(),projectId,templateId,template.kind(),
            feature ? featureRequest.substring(0,Math.min(featureRequest.length(),140)) : "Project discovery",
            "ACTIVE",initial,0,0,template.questions().getFirst().text());
        store.saveInterview(interview);
        return interview;
    }

    public Interview answer(String id,String answer) {
        if (answer == null || answer.isBlank()) throw new IllegalArgumentException("Answer is required");
        Interview current=store.interview(id);
        if (!current.status().equals("ACTIVE")) throw new IllegalArgumentException("Interview is not active");
        Template template=store.template(current.templateId());
        Question q=template.questions().get(current.questionIndex());
        List<Answer> history=new ArrayList<>(current.answers());
        history.add(new Answer(q.id(),current.currentQuestion(),answer.strip()));
        AiAssistant.Evaluation evaluation=ai.assess(q,answer,history);
        boolean askMore=!evaluation.adequate() && current.followUps()<Math.max(0,q.maxFollowUps());
        int index=askMore?current.questionIndex():current.questionIndex()+1;
        String status=index==template.questions().size()?"COMPLETE":"ACTIVE";
        String next=askMore ? evaluation.followUpQuestion() :
            status.equals("COMPLETE") ? "" : template.questions().get(index).text();
        if (askMore && (next == null || next.isBlank())) next="Can you give a concrete example?";
        Interview updated=new Interview(id,current.projectId(),current.templateId(),current.kind(),current.title(),
            status,List.copyOf(history),index,askMore?current.followUps()+1:0,next);
        store.saveInterview(updated);
        return updated;
    }
}

package org.openjcontroltower.domain;

import java.time.Instant;
import java.util.List;

public final class Models {
    private Models() {}
    /** A Git repository with its credential settings. The secret itself is never part of this record. */
    public record Project(String id, String name, String gitUrl, String defaultBranch, String credentialType,
                          String gitUsername, boolean hasSecret, String cockpitProjectId, String workflowId) {}
    public record Question(String id, String text, boolean required, int maxFollowUps, String completionCriteria) {}
    /** {@code builtIn}: seeded by {@code BootstrapTemplates}; cannot be edited or deleted. */
    public record Template(String id, String name, String kind, List<Question> questions, boolean builtIn) {
        public Template(String id, String name, String kind, List<Question> questions) { this(id,name,kind,questions,false); }
    }
    public record SpecTemplate(String id, String name, String content, boolean defaultTemplate) {}
    public record Answer(String questionId, String question, String answer) {}
    public record Interview(String id, String projectId, String templateId, String kind, String title,
                            String status, List<Answer> answers, int questionIndex, int followUps,
                            String currentQuestion, Instant createdAt, Instant updatedAt) {
        /** Timestamps are assigned by the store on save; they are null on a not-yet-saved interview. */
        public Interview(String id, String projectId, String templateId, String kind, String title,
                         String status, List<Answer> answers, int questionIndex, int followUps, String currentQuestion) {
            this(id,projectId,templateId,kind,title,status,answers,questionIndex,followUps,currentQuestion,null,null);
        }
    }
    public record Spec(String id, String projectId, String title, String slug, String status,
                       String body, List<String> dependsOn, String specPath, String branch) {}
    public record Proposal(String title, String summary, List<String> acceptanceCriteria,
                           List<Integer> dependsOnIndexes, List<String> existingDependencyIds) {}
    public record GraphNode(String id, String title, String status, List<String> dependsOn, boolean ready) {}
    public record Graph(List<GraphNode> nodes, List<String> topologicalOrder) {}
}

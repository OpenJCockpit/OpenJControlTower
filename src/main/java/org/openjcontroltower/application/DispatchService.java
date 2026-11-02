package org.openjcontroltower.application;

import org.openjcontroltower.domain.Models.*;
import org.openjcontroltower.infrastructure.CockpitQueueClient;
import org.openjcontroltower.infrastructure.DataStore;
import org.openjcontroltower.infrastructure.DecisionProvider;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class DispatchService {
    private final DataStore store;
    private final SpecService specs;
    private final CockpitQueueClient cockpit;
    private final DecisionProvider decisions;
    private final PolicyGuard policies;
    public DispatchService(DataStore store,SpecService specs,CockpitQueueClient cockpit,DecisionProvider decisions,PolicyGuard policies) {
        this.store=store;this.specs=specs;this.cockpit=cockpit;this.decisions=decisions;this.policies=policies;
    }
    public record QueueCandidate(String specId,String title,String path,int priority,String reason) {}
    public record DispatchOutcome(String specId,String result) {}

    public List<QueueCandidate> candidates(String projectId) {
        store.project(projectId);
        Graph graph=specs.graph(projectId);
        Map<String,Integer> positions=new HashMap<>();
        for (int i=0;i<graph.topologicalOrder().size();i++) positions.put(graph.topologicalOrder().get(i),i);
        return store.specs(projectId).stream()
            .filter(s -> graph.nodes().stream().anyMatch(n -> n.id().equals(s.id()) && n.ready()))
            .filter(s -> !store.hasDispatch(s.id()))
            .map(s -> {
                DecisionProvider.Rank rank=decisions.rank(s);
                return new QueueCandidate(s.id(),s.title(),s.specPath(),rank.score(),rank.reason());
            }).sorted(Comparator.comparingInt(QueueCandidate::priority).reversed()
                .thenComparingInt(c->positions.get(c.specId()))).toList();
    }
    public synchronized List<DispatchOutcome> dispatch(String projectId,boolean repositoryPublished) {
        if (!repositoryPublished)
            throw new IllegalArgumentException("Confirm the specs are committed and accessible to OpenJCockpit first");
        Project project=store.project(projectId);
        List<QueueCandidate> ready=candidates(projectId);
        policies.check("queue.dispatch",PolicyGuard.resource("projectId",projectId,"workflowId",project.workflowId(),
            "specIds",ready.stream().map(QueueCandidate::specId).toList()));
        List<DispatchOutcome> outcomes=new ArrayList<>();
        for (QueueCandidate candidate:ready) {
            Spec spec=store.spec(candidate.specId());
            // Reserve before HTTP: unknown outcomes must not trigger a blind retry.
            store.recordDispatch(spec.id(),"UNKNOWN","Request not yet reconciled");
            try {
                String response=cockpit.enqueue(project.cockpitProjectId(),project.workflowId(),spec.specPath());
                store.updateDispatch(spec.id(),"SENT",response);
                store.setSpecStatus(spec.id(),"DISPATCHED");
                outcomes.add(new DispatchOutcome(spec.id(),"SENT"));
            } catch (RuntimeException ex) {
                store.updateDispatch(spec.id(),"UNKNOWN",ex.getMessage()==null?"Unknown transport failure":ex.getMessage());
                outcomes.add(new DispatchOutcome(spec.id(),"UNKNOWN — reconcile in OpenJCockpit before retry"));
                break;
            }
        }
        return outcomes;
    }
}

package org.openjcontroltower.domain;

import java.util.*;
import static org.openjcontroltower.domain.Models.*;

/** Pure domain logic: every edge denotes a prerequisite, never a mere association. */
public final class DagPlanner {
    public Graph graph(List<Spec> specs) {
        Map<String, Spec> byId = new LinkedHashMap<>();
        for (Spec spec : specs) {
            if (byId.putIfAbsent(spec.id(), spec) != null) {
                throw new IllegalArgumentException("Duplicate spec ID: " + spec.id());
            }
        }
        Map<String, Integer> indegrees = new HashMap<>();
        Map<String, List<String>> next = new HashMap<>();
        for (Spec spec : specs) {
            indegrees.put(spec.id(), 0);
            next.put(spec.id(), new ArrayList<>());
        }
        for (Spec spec : specs) {
            Set<String> seen = new HashSet<>();
            for (String dep : spec.dependsOn()) {
                if (!byId.containsKey(dep)) throw new IllegalArgumentException("Unknown dependency: " + dep);
                if (dep.equals(spec.id())) throw new IllegalArgumentException("Self dependency: " + dep);
                if (!seen.add(dep)) throw new IllegalArgumentException("Repeated dependency: " + dep);
                indegrees.merge(spec.id(), 1, Integer::sum);
                next.get(dep).add(spec.id());
            }
        }
        PriorityQueue<String> ready = new PriorityQueue<>();
        indegrees.forEach((id, count) -> { if (count == 0) ready.add(id); });
        List<String> sorted = new ArrayList<>();
        while (!ready.isEmpty()) {
            String current = ready.remove();
            sorted.add(current);
            for (String child : next.get(current)) {
                if (indegrees.merge(child, -1, Integer::sum) == 0) ready.add(child);
            }
        }
        if (sorted.size() != specs.size()) throw new IllegalArgumentException("Dependency cycle detected");
        List<GraphNode> nodes = specs.stream().map(s -> new GraphNode(s.id(), s.title(), s.status(),
            s.dependsOn(), s.status().equals("APPROVED") && s.dependsOn().stream()
                .allMatch(dep -> byId.get(dep).status().equals("DONE")))).toList();
        return new Graph(nodes, sorted);
    }
}

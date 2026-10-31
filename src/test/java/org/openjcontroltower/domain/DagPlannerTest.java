package org.openjcontroltower.domain;

import org.junit.jupiter.api.Test;
import org.openjcontroltower.domain.Models.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DagPlannerTest {
    private final DagPlanner planner = new DagPlanner();
    private static Spec spec(String id, String state, String... predecessors) {
        return new Spec(id,"project",id,"slug",state,"text",List.of(predecessors),"001-demo/spec.md");
    }
    @Test void ordersPrerequisitesBeforeDependentsAndComputesReadiness() {
        Graph g=planner.graph(List.of(spec("B","APPROVED","A"),spec("A","DONE"),spec("C","APPROVED","B")));
        assertEquals(List.of("A","B","C"),g.topologicalOrder());
        assertTrue(g.nodes().stream().filter(n->n.id().equals("B")).findFirst().orElseThrow().ready());
        assertFalse(g.nodes().stream().filter(n->n.id().equals("C")).findFirst().orElseThrow().ready());
    }
    @Test void handlesEmptyGraph() {
        Graph g=planner.graph(List.of());assertTrue(g.nodes().isEmpty());assertTrue(g.topologicalOrder().isEmpty());
    }
    @Test void sortsIndependentNodesDeterministically() {
        assertEquals(List.of("A","Z"),planner.graph(List.of(spec("Z","APPROVED"),spec("A","APPROVED"))).topologicalOrder());
    }
    @Test void rejectsCycles() {
        assertThrows(IllegalArgumentException.class,()->planner.graph(List.of(spec("A","DRAFT","B"),spec("B","DRAFT","A"))));
    }
    @Test void rejectsUnknownReferences() {
        assertThrows(IllegalArgumentException.class,()->planner.graph(List.of(spec("A","APPROVED","B"))));
    }
    @Test void rejectsDuplicateIds() {
        assertThrows(IllegalArgumentException.class,()->planner.graph(List.of(spec("A","DRAFT"),spec("A","DRAFT"))));
    }
    @Test void rejectsSelfDependency() {
        assertThrows(IllegalArgumentException.class,()->planner.graph(List.of(spec("A","DRAFT","A"))));
    }
    @Test void rejectsRepeatedDependency() {
        assertThrows(IllegalArgumentException.class,()->planner.graph(List.of(spec("A","APPROVED"),spec("B","DRAFT","A","A"))));
    }
}

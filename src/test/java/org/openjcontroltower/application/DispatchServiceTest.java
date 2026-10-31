package org.openjcontroltower.application;

import org.junit.jupiter.api.Test;
import org.openjcontroltower.domain.Models.*;
import org.openjcontroltower.infrastructure.CockpitQueueClient;
import org.openjcontroltower.infrastructure.DataStore;
import org.openjcontroltower.infrastructure.DecisionProvider;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DispatchServiceTest {
    private final DataStore store=mock(DataStore.class);
    private final SpecService specs=mock(SpecService.class);
    private final CockpitQueueClient cockpit=mock(CockpitQueueClient.class);
    private final DecisionProvider decisions=mock(DecisionProvider.class);
    private final DispatchService service=new DispatchService(store,specs,cockpit,decisions);
    private final Project project=new Project("p","Example","/repo","cockpit-1","wf-1");
    private final Spec a=spec("A"),b=spec("B"),c=spec("C"),notReady=spec("D");

    private static Spec spec(String id) { return new Spec(id,"p",id,"slug","APPROVED","body",List.of(),id+"/spec.md"); }
    private static GraphNode node(String id,boolean ready) { return new GraphNode(id,id,"APPROVED",List.of(),ready); }

    private void givenReadyGraph() {
        when(store.project("p")).thenReturn(project);
        when(store.specs("p")).thenReturn(List.of(a,b,c,notReady));
        when(specs.graph("p")).thenReturn(new Graph(List.of(node("A",true),node("B",true),node("C",true),node("D",false)),
            List.of("A","B","C","D")));
        when(store.hasDispatch(anyString())).thenReturn(false);
        when(store.hasDispatch("C")).thenReturn(true);
        when(decisions.rank(a)).thenReturn(new DecisionProvider.Rank(50,"normal"));
        when(decisions.rank(b)).thenReturn(new DecisionProvider.Rank(90,"urgent"));
        when(store.spec("A")).thenReturn(a);
        when(store.spec("B")).thenReturn(b);
    }
    @Test void ranksReadyUndispatchedSpecsByPriorityThenTopology() {
        givenReadyGraph();
        List<DispatchService.QueueCandidate> result=service.candidates("p");
        assertEquals(List.of("B","A"),result.stream().map(DispatchService.QueueCandidate::specId).toList());
        assertEquals(new DispatchService.QueueCandidate("B","B","B/spec.md",90,"urgent"),result.getFirst());
    }
    @Test void requiresPublicationConfirmation() {
        assertThrows(IllegalArgumentException.class,()->service.dispatch("p",false));
        verifyNoInteractions(cockpit);
    }
    @Test void reservesThenSendsEveryReadySpec() {
        givenReadyGraph();
        when(cockpit.enqueue(eq("cockpit-1"),eq("wf-1"),anyString())).thenReturn("{\"id\":1}");
        assertEquals(List.of(new DispatchService.DispatchOutcome("B","SENT"),new DispatchService.DispatchOutcome("A","SENT")),
            service.dispatch("p",true));
        verify(store).recordDispatch("B","UNKNOWN","Request not yet reconciled");
        verify(store).updateDispatch("B","SENT","{\"id\":1}");
        verify(store).setSpecStatus("A","DISPATCHED");
    }
    @Test void haltsOnUnknownOutcomeWithoutRetry() {
        givenReadyGraph();
        when(cockpit.enqueue(anyString(),anyString(),eq("B/spec.md"))).thenThrow(new IllegalStateException("timeout"));
        List<DispatchService.DispatchOutcome> result=service.dispatch("p",true);
        assertEquals(1,result.size());
        assertTrue(result.getFirst().result().startsWith("UNKNOWN"));
        verify(store).updateDispatch("B","UNKNOWN","timeout");
        verify(store,never()).setSpecStatus(anyString(),anyString());
        verify(cockpit,times(1)).enqueue(anyString(),anyString(),anyString());
    }
    @Test void recordsGenericMessageWhenFailureHasNone() {
        givenReadyGraph();
        when(cockpit.enqueue(anyString(),anyString(),anyString())).thenThrow(new RuntimeException());
        service.dispatch("p",true);
        verify(store).updateDispatch("B","UNKNOWN","Unknown transport failure");
    }
}

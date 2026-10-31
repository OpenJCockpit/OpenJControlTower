package org.openjcontroltower.config;

import org.junit.jupiter.api.Test;
import org.openjcontroltower.domain.Models.*;
import org.openjcontroltower.infrastructure.DataStore;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BootstrapTemplatesTest {
    private final DataStore store=mock(DataStore.class);
    @Test void seedsDefaultsIntoEmptyStore() throws Exception {
        when(store.specTemplates()).thenReturn(List.of());
        when(store.templates()).thenReturn(List.of());
        new BootstrapTemplates().seedInterviewTemplates(store).run(null);
        verify(store).saveSpecTemplate(argThat(t->t.defaultTemplate() && t.content().contains("{{SPEC_ID}}")));
        verify(store).saveTemplate(argThat(t->t.kind().equals("PROJECT") && t.questions().size()==4));
        verify(store).saveTemplate(argThat(t->t.kind().equals("FEATURE") && t.questions().size()==4));
    }
    @Test void leavesExistingTemplatesUntouched() throws Exception {
        when(store.specTemplates()).thenReturn(List.of(new SpecTemplate("s","S","x",true)));
        when(store.templates()).thenReturn(List.of(new Template("t","T","PROJECT",List.of())));
        new BootstrapTemplates().seedInterviewTemplates(store).run(null);
        verify(store,never()).saveSpecTemplate(any());
        verify(store,never()).saveTemplate(any());
    }
}

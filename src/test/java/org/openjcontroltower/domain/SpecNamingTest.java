package org.openjcontroltower.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpecNamingTest {
    @Test void idsAreImmutableLookingAndUnique() {
        String a=SpecNaming.newId(),b=SpecNaming.newId();
        assertTrue(a.startsWith("SPEC-"));assertNotEquals(a,b);
    }
    @Test void normalizesAccentsAndPunctuation() {
        assertEquals("ete-a-la-carte",SpecNaming.slug("Été à la carte!"));
    }
    @Test void providesFallbackAndBoundsLength() {
        assertEquals("feature",SpecNaming.slug("!!!"));
        assertTrue(SpecNaming.slug("a".repeat(200)).length()<=65);
    }
}

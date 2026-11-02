package org.openjcontroltower.api;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ApiErrorsTest {
    private final ApiErrors errors=new ApiErrors();
    @Test void mapsClientErrorsToBadRequest() {
        assertEquals(400,errors.badRequest(new IllegalArgumentException("bad")).getStatusCode().value());
        assertEquals(Map.of("error","bad"),errors.badRequest(new IllegalArgumentException("bad")).getBody());
        assertEquals(Map.of("error","Invalid request"),errors.badRequest(new IllegalArgumentException()).getBody());
    }
    @Test void mapsPolicyDenialsToForbidden() {
        var response=errors.denied(new org.openjcontroltower.application.PolicyGuard.PolicyDeniedException("Denied by policy: x"));
        assertEquals(403,response.getStatusCode().value());
        assertEquals(Map.of("error","Denied by policy: x"),response.getBody());
    }
    @Test void mapsStateErrorsToConflict() {
        assertEquals(409,errors.conflict(new IllegalStateException("busy")).getStatusCode().value());
        assertEquals(Map.of("error","busy"),errors.conflict(new IllegalStateException("busy")).getBody());
        assertEquals(Map.of("error","Conflict"),errors.conflict(new IllegalStateException()).getBody());
    }
}

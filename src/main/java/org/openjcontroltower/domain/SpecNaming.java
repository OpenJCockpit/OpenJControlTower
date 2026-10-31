package org.openjcontroltower.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

public final class SpecNaming {
    private SpecNaming() {}
    public static String newId() { return "SPEC-" + UUID.randomUUID(); }
    public static String slug(String title) {
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        String clean = normalized.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (clean.isBlank()) return "feature";
        return clean.substring(0, Math.min(clean.length(), 65)).replaceAll("-$", "");
    }
}

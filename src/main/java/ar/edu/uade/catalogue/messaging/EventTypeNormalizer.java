package ar.edu.uade.catalogue.messaging;

import java.text.Normalizer;

public final class EventTypeNormalizer {
    private EventTypeNormalizer() {}

    public static String normalize(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase();
        String noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return noAccents.trim();
    }
}


package ar.edu.uade.catalogue.messaging;

import java.text.Normalizer;
import java.util.Map;

public final class EventTypeNormalizer {
    private EventTypeNormalizer() {}

    private static final Map<String, String> ALIASES = Map.of(
        "post:comprapendiente", "post: compra pendiente",
        "post:compra confirmada", "post: compra confirmada",
        "post:compraconfirmada", "post: compra confirmada",
        "delete:compracancelada", "delete: compra cancelada",
        "stock_rollback", "post: stock rollback - compra cancelada",
        "stockrollback", "post: stock rollback - compra cancelada"
    );

    public static String normalize(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase();
        String noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD)
                                     .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String noSpaces = noAccents.replaceAll("\\s+", "");
        
        // Buscar primero en alias por si hay una coincidencia directa sin espacios
        if (ALIASES.containsKey(noSpaces)) {
            return ALIASES.get(noSpaces);
        }
        
        // Buscar en alias con el string normalizado (sin acentos)
        if (ALIASES.containsKey(noAccents.trim())) {
            return ALIASES.get(noAccents.trim());
        }

        // Devolver el string normalizado si no hay alias
        return noAccents.trim();
    }
}

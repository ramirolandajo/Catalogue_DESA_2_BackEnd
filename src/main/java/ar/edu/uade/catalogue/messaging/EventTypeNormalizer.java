package ar.edu.uade.catalogue.messaging;

import java.text.Normalizer;

public final class EventTypeNormalizer {
    private EventTypeNormalizer() {}

    public static String normalize(String s) {
        if (s == null) return "";

        // 1. Convertir a minúsculas y quitar acentos para una comparación consistente
        String clean = Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // 2. Usar una cadena de 'if-contains' para una máxima robustez
        if (clean.contains("compra pendiente")) {
            return "post: compra pendiente";
        }
        if (clean.contains("compra confirmada")) {
            return "post: compra confirmada";
        }
        if (clean.contains("stock rollback") || clean.contains("stockrollback") || clean.contains("stockrollback_cartcancelled")) {
            return "post: stock rollback - compra cancelada";
        }
        if (clean.contains("compra cancelada")) {
            return "delete: compra cancelada";
        }

        // 3. Si no coincide con nada, devolver la cadena limpia como último recurso
        return clean.trim();
    }
}

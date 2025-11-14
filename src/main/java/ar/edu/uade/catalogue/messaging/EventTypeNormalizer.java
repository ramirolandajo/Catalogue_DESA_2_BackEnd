package ar.edu.uade.catalogue.messaging;

import java.text.Normalizer;
import java.util.Map;

public final class EventTypeNormalizer {
    private EventTypeNormalizer() {}

    // Mapa de alias a nombres de eventos canónicos
    private static final Map<String, String> ALIASES = Map.ofEntries(
        // Compra Pendiente
        Map.entry("post: compra pendiente", "post: compra pendiente"),
        Map.entry("post:comprapendiente", "post: compra pendiente"),
        Map.entry("compra pendiente", "post: compra pendiente"),
        Map.entry("comprapendiente", "post: compra pendiente"),
        Map.entry("compra_pendiente", "post: compra pendiente"),

        // Compra Confirmada
        Map.entry("post: compra confirmada", "post: compra confirmada"),
        Map.entry("post:compraconfirmada", "post: compra confirmada"),
        Map.entry("compra confirmada", "post: compra confirmada"),
        Map.entry("compraconfirmada", "post: compra confirmada"),
        Map.entry("compra_confirmada", "post: compra confirmada"),

        // Compra Cancelada
        Map.entry("delete: compra cancelada", "delete: compra cancelada"),
        Map.entry("delete:compracancelada", "delete: compra cancelada"),
        Map.entry("compra cancelada", "delete: compra cancelada"),
        Map.entry("compracancelada", "delete: compra cancelada"),
        Map.entry("compra_cancelada", "delete: compra cancelada"),

        // Stock Rollback
        Map.entry("post: stock rollback - compra cancelada", "post: stock rollback - compra cancelada"),
        Map.entry("stock_rollback", "post: stock rollback - compra cancelada"),
        Map.entry("stockrollback", "post: stock rollback - compra cancelada"),
        Map.entry("stockrollback_cartcancelled", "post: stock rollback - compra cancelada")
    );

    public static String normalize(String s) {
        if (s == null) return "";
        
        // 1. Convertir a minúsculas
        String lower = s.toLowerCase();
        
        // 2. Quitar acentos
        String noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD)
                                     .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        
        // 3. Quitar espacios y guiones bajos para una clave de búsqueda consistente
        String searchKey = noAccents.replaceAll("[\\s_]+", "");

        // Buscar en los alias usando la clave sin espacios/guiones
        for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
            String aliasKey = entry.getKey().replaceAll("[\\s_]+", "");
            if (aliasKey.equals(searchKey)) {
                return entry.getValue();
            }
        }

        // Si no se encuentra ningún alias, devolver el string original normalizado (sin acentos y con espacios)
        return noAccents.trim();
    }
}

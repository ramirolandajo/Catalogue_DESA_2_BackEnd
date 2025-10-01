package ar.edu.uade.catalogue.inventario;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import ar.edu.uade.catalogue.service.ProductService;

@Component
@ConditionalOnProperty(prefix = "inventario.kafka.internal", name = "enabled", havingValue = "true")
public class InventarioEventsListener {

    private static final Logger log = LoggerFactory.getLogger(InventarioEventsListener.class);
    private static final String VALUE_DESER_EX_HEADER = "springDeserializerExceptionValue";

    private final EventIdempotencyService idempotency;
    private final InventarioConsumerMonitorService monitor;
    private final ProductService productService;
    private final ObjectMapper objectMapper;

    public InventarioEventsListener(EventIdempotencyService idempotency, InventarioConsumerMonitorService monitor, ProductService productService, ObjectMapper objectMapper) {
        this.idempotency = idempotency;
        this.monitor = monitor;
        this.productService = productService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${inventario.kafka.internal-topic:inventario}",
            groupId = "${spring.kafka.consumer.group-id:inventario-ms}",
            containerFactory = "inventarioKafkaListenerContainerFactory")
    public void onMessage(ConsumerRecord<String, Object> record, Acknowledgment ack, @Payload(required = false) Object ignoredPayload) {
        // Manejo de errores de deserialización: saltar y ACK para evitar while true
        var hdr = record.headers().lastHeader(VALUE_DESER_EX_HEADER);
        if (hdr != null) {
            log.error("[Inventario][Internal] Valor no deserializable. Saltando record topic={} partition={} offset={}", record.topic(), record.partition(), record.offset());
            ack.acknowledge();
            return;
        }

        EventMessage msg;
        Object raw = record.value();
        try {
            if (raw instanceof String s) {
                msg = objectMapper.readValue(s, EventMessage.class);
            } else {
                msg = objectMapper.convertValue(raw, EventMessage.class);
            }
        } catch (Exception ex) {
            log.error("[Inventario][Internal] Error convirtiendo payload en EventMessage: {}. Saltando.", ex.toString());
            ack.acknowledge();
            return;
        }

        if (msg == null) {
            log.warn("[Inventario][Internal] Mensaje nulo en topic={} partition={} offset={}, se ACKea y continúa", record.topic(), record.partition(), record.offset());
            ack.acknowledge();
            return;
        }

        String eventId = msg.getEventId();
        String normalized = msg.getNormalizedEventType();

        if (idempotency.alreadyProcessed(eventId)) {
            monitor.incDuplicate();
            log.info("[Inventario][SkipDuplicate] id={} type={}", eventId, normalized);
            ack.acknowledge();
            return;
        }

        try {
            switch (normalized) {
                case "post: stock actualizado" -> handleStockActualizado(msg);
                case "post: producto desactivado" -> handleProductoDesactivado(msg);
                case "post: producto creado" -> handleProductoCreado(msg);
                case "delete: producto eliminado" -> handleProductoEliminado(msg);
                case "post: reserva de stock" -> handleReservaStock(msg);
                case "delete: reserva cancelada" -> handleReservaCancelada(msg);
                case "post: compra pendiente" -> handleCompraPendiente(msg);
                case "post: compra confirmada" -> handleCompraConfirmada(msg);
                case "delete: compra cancelada" -> handleCompraCancelada(msg);
                case "post: review creada" -> handleReviewCreada(msg);
                default -> log.info("[Inventario][Listener] Evento no reconocido: {} payload={}", normalized, msg.getPayload());
            }
            idempotency.markProcessed(eventId);
            monitor.incHandled(normalized, eventId);
            ack.acknowledge();
        } catch (RuntimeException ex) {
            monitor.incError(normalized);
            log.error("[Inventario][HandlerError] id={} type={} ex={}", eventId, normalized, ex.toString());
            // no ACK -> que el error handler gestione el retry/backoff
            throw ex;
        }
    }

    private void handleStockActualizado(EventMessage msg) { log.info("[Inventario][Handler] Stock actualizado payload={}", msg.getPayload()); }
    private void handleProductoDesactivado(EventMessage msg) { log.info("[Inventario][Handler] Producto desactivado payload={}", msg.getPayload()); }
    private void handleProductoCreado(EventMessage msg) { log.info("[Inventario][Handler] Producto creado payload={}", msg.getPayload()); }
    private void handleProductoEliminado(EventMessage msg) { log.info("[Inventario][Handler] Producto eliminado payload={}", msg.getPayload()); }
    private void handleReservaStock(EventMessage msg) { log.info("[Inventario][Handler] Reserva de stock payload={}", msg.getPayload()); }
    private void handleReservaCancelada(EventMessage msg) { log.info("[Inventario][Handler] Reserva cancelada payload={}", msg.getPayload()); }
    private void handleCompraPendiente(EventMessage msg) { log.info("[Inventario][Handler] Compra pendiente payload={}", msg.getPayload()); }

    @SuppressWarnings("unchecked")
    private void handleCompraConfirmada(EventMessage msg) {
        Object payload = msg.getPayload();
        if (!(payload instanceof Map<?, ?> map)) {
            log.warn("[Inventario][CompraConfirmada] Payload inesperado: {}", payload);
            return;
        }
        Object cartObj = map.get("cart");
        if (!(cartObj instanceof Map<?, ?> cart)) {
            log.warn("[Inventario][CompraConfirmada] Cart ausente o inválido: {}", cartObj);
            return;
        }
        Object itemsObj = cart.get("cartItems");
        if (!(itemsObj instanceof List<?> items)) {
            log.warn("[Inventario][CompraConfirmada] cartItems ausente o inválido: {}", itemsObj);
            return;
        }
        for (Object it : items) {
            if (it instanceof Map<?, ?> item) {
                Integer productCode = readInt(item, "productCode", "product_code", "code");
                Integer qty = readInt(item, "quantity", "qty", "amount");
                if (productCode != null && qty != null && qty > 0) {
                    try { productService.updateStockPostSale(productCode, qty); }
                    catch (Exception ex) { log.error("[Inventario][CompraConfirmada] Error productCode={} qty={} ex={}", productCode, qty, ex.toString()); throw ex instanceof RuntimeException re ? re : new RuntimeException(ex); }
                } else { log.warn("[Inventario][CompraConfirmada] Item inválido: {}", item); }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleCompraCancelada(EventMessage msg) {
        Object payload = msg.getPayload();
        if (!(payload instanceof Map<?, ?> map)) { log.warn("[Inventario][CompraCancelada] Payload inesperado: {}", payload); return; }
        Object cartObj = map.get("cart");
        if (!(cartObj instanceof Map<?, ?> cart)) { log.warn("[Inventario][CompraCancelada] Cart ausente o inválido: {}", cartObj); return; }
        Object itemsObj = cart.get("cartItems");
        if (!(itemsObj instanceof List<?> items)) { log.warn("[Inventario][CompraCancelada] cartItems ausente o inválido: {}", itemsObj); return; }
        for (Object it : items) {
            if (it instanceof Map<?, ?> item) {
                Integer productCode = readInt(item, "productCode", "product_code", "code");
                Integer qty = readInt(item, "quantity", "qty", "amount");
                if (productCode != null && qty != null && qty > 0) {
                    try { productService.updateStockPostCancelation(productCode, qty); }
                    catch (Exception ex) { log.error("[Inventario][CompraCancelada] Error productCode={} qty={} ex={}", productCode, qty, ex.toString()); throw ex instanceof RuntimeException re ? re : new RuntimeException(ex); }
                } else { log.warn("[Inventario][CompraCancelada] Item inválido: {}", item); }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleReviewCreada(EventMessage msg) {
        Object payload = msg.getPayload();
        if (!(payload instanceof Map<?, ?> map)) { log.warn("[Inventario][ReviewCreada] Payload inesperado: {}", payload); return; }
        String message = readString(map, "message", "mensaje");
        Float rateUpdated = readFloat(map, "rateUpdated", "rate", "calification");
        Integer productCode = readInt(map, "productCode", "product_code");
        if (productCode == null) { log.warn("[Inventario][ReviewCreada] No se encontró productCode en payload={}, se omite.", payload); return; }
        try { productService.addReview(productCode, message, rateUpdated); }
        catch (Exception ex) { log.error("[Inventario][ReviewCreada] Error productCode={} ex={}", productCode, ex.toString()); throw ex instanceof RuntimeException re ? re : new RuntimeException(ex); }
    }

    private Integer readInt(Map<?, ?> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v instanceof Number n) return n.intValue();
            if (v instanceof String s) try { return Integer.parseInt(s.trim()); } catch (Exception ignored) {}
        }
        return null;
    }
    private Float readFloat(Map<?, ?> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v instanceof Number n) return n.floatValue();
            if (v instanceof String s) try { return Float.parseFloat(s.trim()); } catch (Exception ignored) {}
        }
        return null;
    }
    private String readString(Map<?, ?> map, String... keys) {
        for (String k : keys) { Object v = map.get(k); if (v != null) return String.valueOf(v); }
        return null;
    }
}

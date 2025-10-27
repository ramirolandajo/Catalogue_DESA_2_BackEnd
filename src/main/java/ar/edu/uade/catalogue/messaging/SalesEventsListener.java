package ar.edu.uade.catalogue.messaging;

import ar.edu.uade.catalogue.model.ConsumedEventLog;
import ar.edu.uade.catalogue.model.ConsumedEventLog.Status;
import ar.edu.uade.catalogue.repository.ConsumedEventLogRepository;
import ar.edu.uade.catalogue.service.InventoryOrderSyncService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class SalesEventsListener {
    private static final Logger log = LoggerFactory.getLogger(SalesEventsListener.class);

    // Header estándar que agrega ErrorHandlingDeserializer al fallar la deserialización del VALUE
    private static final String VALUE_DESER_EX_HEADER = "springDeserializerExceptionValue";

    private final ObjectMapper objectMapper;
    private final ConsumedEventLogRepository logRepo;
    private final InventoryOrderSyncService inventoryService;
    private final CoreApiClient coreApiClient;

    @Value("${inventario.kafka.sales-topic:ventas.events}")
    private String topicName;

    public SalesEventsListener(ObjectMapper objectMapper,
                               ConsumedEventLogRepository logRepo,
                               InventoryOrderSyncService inventoryService,
                               CoreApiClient coreApiClient) {
        this.objectMapper = objectMapper;
        this.logRepo = logRepo;
        this.inventoryService = inventoryService;
        this.coreApiClient = coreApiClient;
    }

    @KafkaListener(topics = "${inventario.kafka.sales-topic:ventas.events}")
    public void onMessage(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            log.info("[VentasConsumer] In msg topic={} partition={} offset={} key={} valueType={}", record.topic(), record.partition(), record.offset(), record.key(), (record.value()==null?"null":record.value().getClass().getName()));
            // 1) Si hubo error de deserialización (ErrorHandlingDeserializer), saltear el record para evitar loop
            var hdr = record.headers().lastHeader(VALUE_DESER_EX_HEADER);
            if (hdr != null) {
                log.error("[VentasConsumer] Valor no deserializable. Saltando record topic={} partition={} offset={}", record.topic(), record.partition(), record.offset());
                ConsumedEventLog cel = logRepo.findByEventId(record.topic() + ":" + record.partition() + ":" + record.offset()).orElseGet(ConsumedEventLog::new);
                if (cel.getId() == null) {
                    cel.setEventId(record.topic() + ":" + record.partition() + ":" + record.offset());
                }
                cel.setStatus(Status.ERROR);
                cel.setAttempts(cel.getAttempts() == null ? 1 : cel.getAttempts() + 1);
                cel.setLastError("Deserialization error; record skipped");
                cel.setTopic(record.topic());
                cel.setPartition(record.partition());
                cel.setOffset(record.offset());
                try { logRepo.save(cel); } catch (Exception persistEx) { log.warn("[VentasConsumer] No se pudo persistir log de deserialización: {}", persistEx.toString()); }
                if (ack != null) ack.acknowledge();
                return;
            }

            Object raw = record.value();
            try {
                ar.edu.uade.catalogue.messaging.EventMessage msg;
                if (raw instanceof String s) {
                    msg = objectMapper.readValue(s, ar.edu.uade.catalogue.messaging.EventMessage.class);
                } else {
                    msg = objectMapper.convertValue(raw, ar.edu.uade.catalogue.messaging.EventMessage.class);
                }
                String eventId = Optional.ofNullable(msg.getEventId()).orElse(record.topic() + ":" + record.partition() + ":" + record.offset());

                // Preferir tipo anidado en payload.type si existe; si no, usar top-level eventType
                String nestedType = null;
                try {
                    JsonNode p = msg.getPayload();
                    if (p != null && p.hasNonNull("type")) nestedType = p.get("type").asText();
                } catch (Exception ignored) {}
                String type = nestedType != null ? nestedType : msg.getEventType();
                String normalized = EventTypeNormalizer.normalize(type);

                // Unwrap payload si viene dentro de payload.payload
                JsonNode effectivePayload = msg.getPayload();
                if (effectivePayload != null && effectivePayload.has("payload") && !effectivePayload.get("payload").isNull()) {
                    effectivePayload = effectivePayload.get("payload");
                }

                ConsumedEventLog cel = logRepo.findByEventId(eventId).orElseGet(ConsumedEventLog::new);
                if (cel.getId() == null) {
                    cel.setEventId(eventId);
                    cel.setStatus(Status.PENDING);
                    cel.setAttempts(0);
                    cel.setAckSent(false);
                    cel.setAckAttempts(0);
                }
                cel.setEventType(type);
                cel.setOriginModule(msg.getOriginModule());
                cel.setTimestampRaw(msg.getTimestampRaw() == null ? null : msg.getTimestampRaw().toString());
                cel.setTopic(record.topic());
                cel.setPartition(record.partition());
                cel.setOffset(record.offset());
                cel.setPayloadJson(raw == null ? null : String.valueOf(raw));
                try { logRepo.save(cel); } catch (Exception persistEx) { log.warn("[VentasConsumer] No se pudo persistir log inicial para eventId={} (se continúa): {}", eventId, persistEx.toString()); }

                if (cel.getStatus() == Status.PROCESSED) {
                    if (Boolean.FALSE.equals(cel.getAckSent())) {
                        try { tryAck(cel); } catch (Exception ackEx) { log.warn("[VentasConsumer] ACK pendiente falló eventId={}: {}", cel.getEventId(), ackEx.toString()); }
                    }
                    if (ack != null) ack.acknowledge();
                    return;
                }

                // Dispatch (incluye alias sin tildes ni espacios para rollback)
                switch (normalized) {
                    case "post: compra pendiente" -> inventoryService.reserveStock(effectivePayload);
                    case "post: compra confirmada" -> inventoryService.confirmStock(effectivePayload);
                    case "delete: compra cancelada" -> inventoryService.cancelReservation(effectivePayload);
                    case "post: stock rollback - compra cancelada", "stockrollback_cartcancelled" -> inventoryService.applyRollback(effectivePayload);
                    default -> log.info("[VentasConsumer] Ignorado eventType='{}' (normalized='{}')", type, normalized);
                }

                // OK -> marcar procesado y ACK (resiliente)
                cel.setStatus(Status.PROCESSED);
                cel.setAttempts((cel.getAttempts() == null ? 0 : cel.getAttempts()) + 1);
                cel.setLastError(null);
                try { logRepo.save(cel); } catch (Exception persistEx) { log.warn("[VentasConsumer] No se pudo persistir PROCESSED para eventId={} (se continúa): {}", cel.getEventId(), persistEx.toString()); }
                try { tryAck(cel); } catch (Exception ackEx) { log.warn("[VentasConsumer] ACK falló para eventId={} (se reintentará por scheduler): {}", cel.getEventId(), ackEx.toString()); }
                if (ack != null) ack.acknowledge();
            } catch (Exception ex) {
                log.error("[VentasConsumer] Error procesando record topic={} partition={} offset={}", record.topic(), record.partition(), record.offset(), ex);
                try {
                    ConsumedEventLog cel = logRepo.findByEventId(record.topic() + ":" + record.partition() + ":" + record.offset()).orElse(null);
                    if (cel != null) {
                        cel.setStatus(Status.ERROR);
                        cel.setAttempts((cel.getAttempts() == null ? 0 : cel.getAttempts()) + 1);
                        cel.setLastError(ex.toString());
                        logRepo.save(cel);
                    }
                } catch (Exception persistEx) {
                    log.warn("[VentasConsumer] No se pudo persistir estado ERROR para el record (se continuará): {}", persistEx.toString());
                }
                if (ack != null) ack.acknowledge();
            }
        } catch (Throwable t) {
            log.error("[VentasConsumer] Throwable no capturado en listener", t);
            try { if (ack != null) ack.acknowledge(); } catch (Exception ignored) {}
        }
    }

    private void tryAck(ConsumedEventLog cel) {
        try {
            coreApiClient.ackEvent(cel.getEventId());
            cel.setAckSent(true);
            cel.setAckAttempts((cel.getAckAttempts() == null ? 0 : cel.getAckAttempts()) + 1);
            cel.setAckLastError(null);
            cel.setAckLastAt(LocalDateTime.now());
        } catch (Exception e) {
            cel.setAckSent(false);
            cel.setAckAttempts((cel.getAckAttempts() == null ? 0 : cel.getAckAttempts()) + 1);
            cel.setAckLastError(e.toString());
            cel.setAckLastAt(LocalDateTime.now());
            throw e;
        } finally {
            try { logRepo.save(cel); } catch (Exception persistEx) { log.warn("[VentasConsumer] No se pudo persistir estado de ACK para eventId={}: {}", cel.getEventId(), persistEx.toString()); }
        }
    }
}

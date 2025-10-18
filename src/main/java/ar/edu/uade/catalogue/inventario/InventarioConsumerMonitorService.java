package ar.edu.uade.catalogue.inventario;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class InventarioConsumerMonitorService {

    private final long startTime = Instant.now().toEpochMilli();

    private final ConcurrentHashMap<String, AtomicLong> handledByType = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> errorsByType = new ConcurrentHashMap<>();
    private final AtomicLong duplicates = new AtomicLong(0);
    private final ConcurrentHashMap<String, String> lastEventIdByType = new ConcurrentHashMap<>();

    @Value("${inventario.kafka.topic:inventario.events}")
    private String topic;
    @Value("${spring.kafka.consumer.group-id:inventario-ms}")
    private String groupId;
    @Value("${inventario.kafka.concurrency:3}")
    private int concurrency;
    @Value("${inventario.kafka.error.maxAttempts:3}")
    private int maxAttempts;
    @Value("${inventario.kafka.error.backoff.ms:500}")
    private long backoffMs;
    @Value("${inventario.kafka.dlq.enabled:false}")
    private boolean dlqEnabled;

    public void incHandled(String type, String eventId) {
        handledByType.computeIfAbsent(type, t -> new AtomicLong()).incrementAndGet();
        if (eventId != null) lastEventIdByType.put(type, eventId);
    }

    public void incError(String type) {
        errorsByType.computeIfAbsent(type, t -> new AtomicLong()).incrementAndGet();
    }

    public void incDuplicate() { duplicates.incrementAndGet(); }

    public Map<String, Long> snapshotHandled() {
        return handledByType.entrySet().stream().collect(ConcurrentHashMap::new, (m,e)->m.put(e.getKey(), e.getValue().get()), Map::putAll);
    }
    public Map<String, Long> snapshotErrors() {
        return errorsByType.entrySet().stream().collect(ConcurrentHashMap::new, (m,e)->m.put(e.getKey(), e.getValue().get()), Map::putAll);
    }

    public Map<String, Object> healthPayload() {
        Map<String, Object> res = new ConcurrentHashMap<>();
        res.put("handledByType", snapshotHandled());
        res.put("errorsByType", snapshotErrors());
        res.put("duplicates", duplicates.get());
        res.put("lastEventIds", Collections.unmodifiableMap(lastEventIdByType));
        res.put("uptimeMs", Instant.now().toEpochMilli() - startTime);
        Map<String, Object> cfg = new ConcurrentHashMap<>();
        cfg.put("topic", topic);
        cfg.put("groupId", groupId);
        cfg.put("concurrency", concurrency);
        cfg.put("maxAttempts", maxAttempts);
        cfg.put("backoffMs", backoffMs);
        cfg.put("dlqEnabled", dlqEnabled);
        res.put("config", cfg);
        return res;
    }
}


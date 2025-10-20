package ar.edu.uade.catalogue.inventario;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class EventIdempotencyService {
    private final Cache<String, Boolean> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .maximumSize(100_000)
            .build();

    public boolean alreadyProcessed(String eventId) {
        if (eventId == null || eventId.isBlank()) return false; // si no hay id, no aplicamos idempotencia
        Boolean exists = cache.getIfPresent(eventId);
        return exists != null && exists;
    }

    public void markProcessed(String eventId) {
        if (eventId == null || eventId.isBlank()) return;
        cache.put(eventId, Boolean.TRUE);
    }
}


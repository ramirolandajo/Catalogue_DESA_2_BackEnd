package ar.edu.uade.catalogue.scheduler;

import ar.edu.uade.catalogue.model.ConsumedEventLog;
import ar.edu.uade.catalogue.model.ConsumedEventLog.Status;
import ar.edu.uade.catalogue.repository.ConsumedEventLogRepository;
import ar.edu.uade.catalogue.messaging.CoreApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@EnableScheduling
public class SalesEventsRetryScheduler {
    private static final Logger log = LoggerFactory.getLogger(SalesEventsRetryScheduler.class);

    private final ConsumedEventLogRepository repo;
    private final CoreApiClient coreApi;

    @Value("${inventario.retry.enabled:true}")
    private boolean enabled;
    @Value("${inventario.retry.maxAttempts:5}")
    private int maxAttempts;
    @Value("${inventario.retry.cooldown.ms:1800000}")
    private long cooldownMs;

    public SalesEventsRetryScheduler(ConsumedEventLogRepository repo, CoreApiClient coreApi) {
        this.repo = repo;
        this.coreApi = coreApi;
    }

    @Scheduled(cron = "${inventario.retry.cron:0 0 */6 * * *}")
    public void retryLoop() {
        if (!enabled) return;
        LocalDateTime threshold = LocalDateTime.now().minusNanos(cooldownMs * 1_000_000);

        // Reintentar ACKs pendientes
        List<ConsumedEventLog> processed = repo.findByStatus(Status.PROCESSED);
        for (ConsumedEventLog cel : processed) {
            if (!Boolean.TRUE.equals(cel.getAckSent()) && cel.getAckAttempts() < maxAttempts) {
                try {
                    coreApi.ackEvent(cel.getEventId());
                    cel.setAckSent(true);
                    cel.setAckAttempts(cel.getAckAttempts() + 1);
                    cel.setAckLastError(null);
                    cel.setAckLastAt(LocalDateTime.now());
                    repo.save(cel);
                } catch (Exception e) {
                    cel.setAckAttempts(cel.getAckAttempts() + 1);
                    cel.setAckLastError(e.toString());
                    cel.setAckLastAt(LocalDateTime.now());
                    repo.save(cel);
                }
            }
        }

        // Reprocesar PENDING/ERROR con cooldown y límite
        for (Status st : new Status[]{Status.PENDING, Status.ERROR}) {
            List<ConsumedEventLog> list = repo.findByStatusAndUpdatedAtBefore(st, threshold);
            for (ConsumedEventLog cel : list) {
                if (cel.getAttempts() >= maxAttempts) continue;
                // no re-procesamos el negocio aquí para evitar duplicados; nos apoyamos en el retry del consumer.
                // Podemos intentar sólo ACK si ya estaba PROCESSED (no aplica en estos estados)
                log.debug("[Retry] Evento {} en estado {} aún pendiente, attempts={}", cel.getEventId(), st, cel.getAttempts());
            }
        }
    }
}


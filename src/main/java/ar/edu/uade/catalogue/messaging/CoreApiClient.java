package ar.edu.uade.catalogue.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class CoreApiClient {

    private static final Logger log = LoggerFactory.getLogger(CoreApiClient.class);

    private final RestTemplate restTemplate;

    @Value("${communication.intermediary.url:http://localhost:8090}")
    private String intermediaryBaseUrl;

    @Value("${communication.origin-module:#{null}}")
    private String configuredOriginModule;

    @Value("${keycloak.client-id}")
    private String clientId;

    public CoreApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // Incluimos ambos campos: eventType (compatibilidad) y type (requerido por el schema actual)
    public record CoreEvent(String eventId, String eventType, String type, BigDecimal timestamp, String originModule, Object payload) {}

    public void postEvent(String type, Object payload, OffsetDateTime timestamp) {
        String origin = (configuredOriginModule != null && !configuredOriginModule.isBlank()) ? configuredOriginModule : clientId;
        // timestamp como segundos+nanos (9 decimales) desde epoch UTC
        long epochSecond = timestamp.toEpochSecond();
        int nano = timestamp.getNano();
        BigDecimal ts = BigDecimal.valueOf(epochSecond).add(BigDecimal.valueOf(nano, 9));

        CoreEvent coreEvent = new CoreEvent(UUID.randomUUID().toString(), type, type, ts, origin, payload);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.postForLocation(intermediaryBaseUrl + "/events", new HttpEntity<>(coreEvent, headers));
            log.info("[CoreApi] POST /events OK type='{}' origin='{}'", type, origin);
        } catch (RestClientException e) {
            log.error("[CoreApi] Error POST /events type='{}': {}", type, e.getMessage());
            throw e;
        }
    }

    public void ackEvent(String eventId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            restTemplate.postForLocation(intermediaryBaseUrl + "/events/" + eventId + "/ack", new HttpEntity<>("{}", headers));
            log.info("[CoreApi] ACK OK eventId='{}'", eventId);
        } catch (RestClientException e) {
            log.error("[CoreApi] Error ACK eventId='{}': {}", eventId, e.getMessage());
            throw e;
        }
    }
}

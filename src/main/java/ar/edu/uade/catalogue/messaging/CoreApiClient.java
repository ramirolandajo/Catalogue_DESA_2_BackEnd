package ar.edu.uade.catalogue.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
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

        long epochMilli = timestamp.toInstant().toEpochMilli();
        BigDecimal ts = BigDecimal.valueOf(epochMilli);

        CoreEvent coreEvent = new CoreEvent(UUID.randomUUID().toString(), type, type, ts, origin, payload);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        try {
            restTemplate.postForLocation(intermediaryBaseUrl + "/events", new HttpEntity<>(coreEvent, headers));
            log.info("[CoreApi] POST /events OK type='{}' origin='{}'", type, origin);
        } catch (HttpStatusCodeException e) {
            String body = e.getResponseBodyAsString();
            log.error("[CoreApi] Error POST /events type='{}': {} {} body={}", type, e.getStatusCode().value(), e.getStatusText(), safe(body));
            throw e;
        } catch (RestClientException e) {
            log.error("[CoreApi] Error POST /events type='{}': {}", type, e.getMessage());
            throw e;
        }
    }

    public void ackEvent(String eventId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        try {
            restTemplate.postForLocation(intermediaryBaseUrl + "/events/" + eventId + "/ack", new HttpEntity<>("{}", headers));
            log.info("[CoreApi] ACK OK eventId='{}'", eventId);
        } catch (HttpStatusCodeException e) {
            String body = e.getResponseBodyAsString();
            log.error("[CoreApi] Error ACK eventId='{}': {} {} body={}", eventId, e.getStatusCode().value(), e.getStatusText(), safe(body));
            throw e;
        } catch (RestClientException e) {
            log.error("[CoreApi] Error ACK eventId='{}': {}", eventId, e.getMessage());
            throw e;
        }
    }

    private String safe(String s) {
        if (s == null) return "<null>";
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }
}

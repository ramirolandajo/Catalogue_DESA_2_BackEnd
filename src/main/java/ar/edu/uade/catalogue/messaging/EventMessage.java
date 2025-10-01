package ar.edu.uade.catalogue.messaging;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EventMessage {
    @JsonProperty("eventId")
    private String eventId;
    @JsonProperty("eventType")
    private String eventType;
    @JsonProperty("payload")
    private JsonNode payload; // lo dejamos genérico
    @JsonProperty("originModule")
    private String originModule;
    @JsonProperty("timestamp")
    private JsonNode timestamp; // puede ser string o número

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public JsonNode getPayload() { return payload; }
    public String getOriginModule() { return originModule; }
    public JsonNode getTimestampRaw() { return timestamp; }

    public OffsetDateTime getTimestampAsOffset() {
        if (timestamp == null || timestamp.isNull()) return null;
        try {
            if (timestamp.isNumber()) {
                // Puede venir en segundos con parte decimal
                double v = timestamp.asDouble();
                long seconds = (long) v;
                long nanos = (long) Math.round((v - seconds) * 1_000_000_000L);
                return OffsetDateTime.ofInstant(Instant.ofEpochSecond(seconds, nanos), ZoneOffset.UTC);
            }
            if (timestamp.isTextual()) {
                return OffsetDateTime.parse(timestamp.asText());
            }
        } catch (Exception ignored) { }
        return null;
    }
}


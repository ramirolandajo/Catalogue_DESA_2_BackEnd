package ar.edu.uade.catalogue.inventario;

import java.text.Normalizer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Objects;

public class EventMessage {
    private String eventId; // UUID as string
    private String eventType;
    private Object payload;
    private String originModule;
    private Object timestamp; // ISO-8601 string or numeric epoch (seconds or millis)

    public EventMessage() {}

    public EventMessage(String eventId, String eventType, Object payload, String originModule, Object timestamp) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
        this.originModule = originModule;
        this.timestamp = timestamp;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }

    public String getOriginModule() { return originModule; }
    public void setOriginModule(String originModule) { this.originModule = originModule; }

    public Object getTimestamp() { return timestamp; }
    public void setTimestamp(Object timestamp) { this.timestamp = timestamp; }

    // Utilitarios
    public OffsetDateTime getTimestampAsOffsetDateTime() {
        if (timestamp == null) return OffsetDateTime.now(ZoneOffset.UTC);
        try {
            if (timestamp instanceof Number num) {
                long v = num.longValue();
                // if looks like seconds (10 digits) convert to seconds else millis
                if (String.valueOf(Math.abs(v)).length() <= 10) {
                    return OffsetDateTime.ofInstant(Instant.ofEpochSecond(v), ZoneOffset.UTC);
                } else {
                    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(v), ZoneOffset.UTC);
                }
            }
            String s = String.valueOf(timestamp).trim();
            // try ISO-8601
            try {
                return OffsetDateTime.parse(s);
            } catch (DateTimeParseException e) {
                // maybe epoch seconds or millis as string
                long v = Long.parseLong(s);
                if (s.length() <= 10) {
                    return OffsetDateTime.ofInstant(Instant.ofEpochSecond(v), ZoneOffset.UTC);
                } else {
                    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(v), ZoneOffset.UTC);
                }
            }
        } catch (Exception ex) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public String getNormalizedEventType() {
        if (eventType == null) return "";
        String s = Normalizer.normalize(eventType, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", ""); // remove diacritics
        return s.toLowerCase().trim();
    }

    @Override
    public String toString() {
        return "EventMessage{" +
                "eventId='" + eventId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", originModule='" + originModule + '\'' +
                ", timestamp=" + Objects.toString(timestamp) +
                '}';
    }
}


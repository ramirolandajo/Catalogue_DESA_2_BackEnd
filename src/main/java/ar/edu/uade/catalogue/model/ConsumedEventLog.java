package ar.edu.uade.catalogue.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "consumed_event_log", indexes = {
        @Index(name = "idx_consumed_event_event_id", columnList = "event_id", unique = true),
        @Index(name = "idx_consumed_event_status", columnList = "status")
})
public class ConsumedEventLog {

    public enum Status { PENDING, PROCESSED, ERROR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "origin_module")
    private String originModule;

    @Lob
    @Column(name = "timestamp_raw")
    private String timestampRaw;

    @Column(name = "topic")
    private String topic;

    @Column(name = "partition_no")
    private Integer partition; // renombrado para evitar palabra reservada

    @Column(name = "offset_val")
    private Long offset;

    @Lob
    @Column(name = "payload_json")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status = Status.PENDING;

    @Column(name = "attempts")
    private Integer attempts = 0;

    @Lob
    @Column(name = "last_error")
    private String lastError;

    @Column(name = "ack_sent")
    private Boolean ackSent = false;

    @Column(name = "ack_attempts")
    private Integer ackAttempts = 0;

    @Lob
    @Column(name = "ack_last_error")
    private String ackLastError;

    @Column(name = "ack_last_at")
    private LocalDateTime ackLastAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }

    // getters y setters
    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getOriginModule() { return originModule; }
    public void setOriginModule(String originModule) { this.originModule = originModule; }
    public String getTimestampRaw() { return timestampRaw; }
    public void setTimestampRaw(String timestampRaw) { this.timestampRaw = timestampRaw; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public Integer getPartition() { return partition; }
    public void setPartition(Integer partition) { this.partition = partition; }
    public Long getOffset() { return offset; }
    public void setOffset(Long offset) { this.offset = offset; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Boolean getAckSent() { return ackSent; }
    public void setAckSent(Boolean ackSent) { this.ackSent = ackSent; }
    public Integer getAckAttempts() { return ackAttempts; }
    public void setAckAttempts(Integer ackAttempts) { this.ackAttempts = ackAttempts; }
    public String getAckLastError() { return ackLastError; }
    public void setAckLastError(String ackLastError) { this.ackLastError = ackLastError; }
    public LocalDateTime getAckLastAt() { return ackLastAt; }
    public void setAckLastAt(LocalDateTime ackLastAt) { this.ackLastAt = ackLastAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

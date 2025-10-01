package ar.edu.uade.catalogue.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.kafka.event.ConsumerStartedEvent;
import org.springframework.kafka.event.ConsumerStoppedEvent;

@Component
public class KafkaAssignmentLogger {
    private static final Logger log = LoggerFactory.getLogger(KafkaAssignmentLogger.class);

    @EventListener
    public void onConsumerStarted(ConsumerStartedEvent event) {
        log.info("[Kafka][Consumer] STARTED container={}", event.getSource());
    }

    @EventListener
    public void onConsumerStopped(ConsumerStoppedEvent event) {
        log.info("[Kafka][Consumer] STOPPED container={}", event.getSource());
    }
}

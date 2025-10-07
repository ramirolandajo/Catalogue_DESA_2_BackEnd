package ar.edu.uade.catalogue.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@ConditionalOnProperty(prefix = "inventario.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaListenersBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KafkaListenersBootstrap.class);

    private final KafkaListenerEndpointRegistry registry;

    @Value("${spring.kafka.listener.auto-startup:false}")
    private boolean kafkaAutoStartup;

    public KafkaListenersBootstrap(KafkaListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!kafkaAutoStartup) {
            log.info("[Kafka][Bootstrap] Iniciando listeners manualmente tras probe OK...");
            registry.getListenerContainers().forEach(container -> {
                try {
                    container.start();
                    log.info("[Kafka][Bootstrap] Listener '{}' iniciado", container.getGroupId());
                } catch (Exception e) {
                    log.error("[Kafka][Bootstrap] Error iniciando listener '{}': {}", container.getGroupId(), e.toString());
                    throw e;
                }
            });
        } else {
            log.info("[Kafka][Bootstrap] auto-startup=true => listeners ya iniciarán por configuración");
        }
    }
}


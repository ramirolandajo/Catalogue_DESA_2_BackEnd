package ar.edu.uade.catalogue.messaging;
import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.admin.AdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

    @Component
    public class KafkaConnectivityProbe {

        private static final Logger log = LoggerFactory.getLogger(KafkaConnectivityProbe.class);

        private final KafkaAdmin kafkaAdmin;

        // Spring Boot autowires the KafkaAdmin bean automatically using all your spring.kafka.* props
        public KafkaConnectivityProbe(KafkaAdmin kafkaAdmin) {
            this.kafkaAdmin = kafkaAdmin;
        }

        @PostConstruct
        public void verifyConnectivityOnStartup() {
            log.info("[KafkaStartup] Verificando conexión a Kafka...");

            Map<String, Object> cfg = new HashMap<>(kafkaAdmin.getConfigurationProperties());

            Exception lastEx = null;

            for (int attempt = 1; attempt <= 3; attempt++) {
                try (AdminClient admin = AdminClient.create(cfg)) {
                    admin.describeCluster().nodes().get(5000, TimeUnit.MILLISECONDS);
                    log.info("[KafkaStartup] ✅ Conexión a Kafka OK");
                    return;
                } catch (Exception ex) {
                    lastEx = ex;
                    log.warn("[KafkaStartup] Falló intento {}/3: {}", attempt, ex.toString());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            log.error("[KafkaStartup] ❌ No se pudo conectar a Kafka tras 3 intentos", lastEx);
            throw new IllegalStateException("Kafka no disponible al iniciar", lastEx);
        }
    }

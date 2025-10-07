package ar.edu.uade.catalogue.messaging;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Order(1)
@ConditionalOnProperty(prefix = "inventario.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConnectivityProbe implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(KafkaConnectivityProbe.class);

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${inventario.kafka.sales-topic:ventas}")
    private String salesTopic;

    @Value("${inventario.kafka.connect.maxAttempts:3}")
    private int maxAttempts;

    @Value("${inventario.kafka.connect.backoff.ms:1500}")
    private long backoffMs;

    @Value("${inventario.kafka.connect.apiTimeout.ms:2000}")
    private long apiTimeoutMs;

    @Override
    public void run(ApplicationArguments args) {
        Exception last = null;
        for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
            try (AdminClient admin = AdminClient.create(Map.of(
                    AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                    AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(apiTimeoutMs),
                    AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, String.valueOf(apiTimeoutMs)
            ))) {
                var names = admin.listTopics().names().get(apiTimeoutMs, TimeUnit.MILLISECONDS);
                log.info("[Kafka][Probe] Broker OK '{}' - topics enumerados: {}", bootstrapServers, names.size());

                DescribeTopicsResult dtr = admin.describeTopics(Collections.singletonList(salesTopic));
                TopicDescription td = dtr.values().get(salesTopic).get(apiTimeoutMs, TimeUnit.MILLISECONDS);
                log.info("[Kafka][Probe] Topic '{}' OK: partitions={}, internal={}", salesTopic, td.partitions().size(), td.isInternal());
                return; // éxito
            } catch (Exception ex) {
                last = ex;
                log.warn("[Kafka][Probe] Intento {} de {} fallido conectando a '{}' / topic '{}': {}", attempt, maxAttempts, bootstrapServers, salesTopic, ex.toString());
                if (attempt < maxAttempts) {
                    try { Thread.sleep(backoffMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
        String msg = String.format("No se pudo conectar a Kafka en '%s' o verificar el topic '%s' tras %d intentos", bootstrapServers, salesTopic, maxAttempts);
        log.error("[Kafka][Probe] {}. Último error: {}", msg, last != null ? last.toString() : "");
        throw new IllegalStateException(msg, last);
    }
}

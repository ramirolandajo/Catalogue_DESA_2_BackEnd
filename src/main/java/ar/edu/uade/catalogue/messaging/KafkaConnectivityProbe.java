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
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "inventario.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConnectivityProbe implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(KafkaConnectivityProbe.class);

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${inventario.kafka.sales-topic:ventas}")
    private String salesTopic;

    @Override
    public void run(ApplicationArguments args) {
        try (AdminClient admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            // Llamadas rápidas con timeout bajo para no trabar el arranque
            var names = admin.listTopics().names().get();
            log.info("[Kafka][Probe] Broker OK '{}' - topics enumerados: {}", bootstrapServers, names.size());

            DescribeTopicsResult dtr = admin.describeTopics(Collections.singletonList(salesTopic));
            TopicDescription td = dtr.values().get(salesTopic).get();
            log.info("[Kafka][Probe] Topic '{}' OK: partitions={}, internal={}", salesTopic, td.partitions().size(), td.isInternal());
        } catch (Exception ex) {
            log.warn("[Kafka][Probe] No se pudo verificar topic '{}' en '{}': {}", salesTopic, bootstrapServers, ex.toString());
        }
    }
}


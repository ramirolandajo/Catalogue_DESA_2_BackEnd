package ar.edu.uade.catalogue.inventario;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
@ConditionalOnProperty(prefix = "inventario.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicsConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${inventario.kafka.topic:inventario}")
    private String inventarioTopicName;

    @Value("${inventario.kafka.connect.apiTimeout.ms:2000}")
    private long apiTimeoutMs;

    @Bean
    @ConditionalOnProperty(prefix = "inventario.kafka", name = "admin.enabled", havingValue = "true", matchIfMissing = false)
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(apiTimeoutMs));
        configs.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, String.valueOf(apiTimeoutMs));
        KafkaAdmin admin = new KafkaAdmin(configs);
        // No fallar antes que nuestro probe maneje los reintentos
        admin.setFatalIfBrokerNotAvailable(false);
        return admin;
    }

    @Bean
    @ConditionalOnProperty(prefix = "inventario.kafka", name = "create-topics", havingValue = "true", matchIfMissing = false)
    public NewTopic inventarioTopic() {
        return TopicBuilder.name(inventarioTopicName)
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "1")
                .build();
    }
}

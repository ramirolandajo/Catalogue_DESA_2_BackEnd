package ar.edu.uade.catalogue.inventario;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

@EnableKafka
@Configuration
@ConditionalOnProperty(prefix = "inventario.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaInventarioConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaInventarioConsumerConfig.class);

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:inventario-ms}")
    private String groupId;

    @Value("${inventario.kafka.error.maxAttempts:3}")
    private int maxAttempts;

    @Value("${inventario.kafka.error.backoff.ms:500}")
    private long backoffMs;

    @Value("${inventario.kafka.dlq.enabled:false}")
    private boolean dlqEnabled;

    @Value("${inventario.kafka.topic:inventario}")
    private String mainTopic;

    @Value("${inventario.kafka.dlq.topicSuffix:.dlq}")
    private String dlqSuffix;

    @Value("${inventario.kafka.concurrency:3}")
    private Integer concurrency;

    @Value("${spring.kafka.listener.auto-startup:false}")
    private boolean autoStartup;

    @Bean
    public ConsumerFactory<String, Object> inventarioConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Configurar ErrorHandlingDeserializer envolviendo JsonDeserializer
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        // Deserializar VALUE como Map para que el listener convierta al POJO esperado
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, java.util.LinkedHashMap.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    // Exponer bean con ambos nombres esperados por @KafkaListener
    @Bean(name = {"inventarioKafkaListenerContainerFactory", "kafkaListenerContainerFactory"})
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> inventarioConsumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(inventarioConsumerFactory);
        if (concurrency != null) {
            factory.setConcurrency(concurrency);
        }
        factory.setAutoStartup(autoStartup);
        factory.getContainerProperties().setMissingTopicsFatal(false);
        // Converter para headers/strings, por si llega como texto
        factory.setRecordMessageConverter(new StringJsonMessageConverter());
        // Ack manual para permitir usar Acknowledgment en los listeners
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setSyncCommits(true);

        // Rebalance listener para logs de asignación/revocación de particiones
        factory.getContainerProperties().setConsumerRebalanceListener(new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsAssigned(java.util.Collection<TopicPartition> partitions) {
                log.info("[Kafka][Assign] groupId={} assignedPartitions={}", groupId, partitions);
            }
            @Override
            public void onPartitionsRevoked(java.util.Collection<TopicPartition> partitions) {
                log.info("[Kafka][Revoke] groupId={} revokedPartitions={}", groupId, partitions);
            }
        });

        FixedBackOff backoff = new FixedBackOff(backoffMs, Math.max(0, maxAttempts - 1));
        DefaultErrorHandler errorHandler;
        if (dlqEnabled) {
            DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> {
                    String dlqTopic = mainTopic + dlqSuffix;
                    log.warn("[Kafka][DLQ] Enviando a {} por error: {}", dlqTopic, ex.getMessage());
                    return new org.apache.kafka.common.TopicPartition(dlqTopic, record.partition());
                }
            );
            errorHandler = new DefaultErrorHandler(recoverer, backoff);
        } else {
            errorHandler = new DefaultErrorHandler(backoff);
        }
        errorHandler.setRetryListeners((rec, ex, deliveryAttempt) -> {
            try {
                log.warn("[Kafka][Retry] intento {} fallido topic={} partition={} offset={} key={} value={} ex=\n", deliveryAttempt,
                        rec.topic(), rec.partition(), rec.offset(), rec.key(), String.valueOf(rec.value()), ex);
            } catch (Exception logEx) {
                log.warn("[Kafka][Retry] intento {} fallido; error al loguear el record: {}", deliveryAttempt, logEx.toString());
            }
        });
        errorHandler.setCommitRecovered(true);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}

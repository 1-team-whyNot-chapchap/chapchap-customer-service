package com.chapchap.customer.global.config;

import com.chapchap.customer.global.kafka.consumer.CustomerKafkaEventConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicContractTest {
    // Explicit peer-service contract: a generic prefix assertion would miss a wrong event name.
    private static final Map<String, String> EXPECTED_TOPICS = Map.of(
            "payment", "msa4-team1.subscription.payment-events.v1",
            "refund", "msa4-team1.subscription.refund-events.v1",
            "subscription-notification", "msa4-team1.subscription.customer-notification-events.v1",
            "subscription", "msa4-team1.subscription.subscription-events.v1",
            "delivery-address", "msa4-team1.subscription.delivery-address-events.v1",
            "delivery", "msa4-team1.delivery.delivery-events.v1",
            "delivery-operation-notification", "msa4-team1.delivery.operation-notification-requests.v1"
    );

    @ParameterizedTest
    @ValueSource(strings = {"src/main/resources/application.yaml", "src/test/resources/application.yaml"})
    void listenerTopicsAndDeadLettersMatchPeerContracts(String yamlPath) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        new YamlPropertySourceLoader().load("contract", new FileSystemResource(yamlPath))
                .forEach(environment.getPropertySources()::addLast);
        EXPECTED_TOPICS.forEach((key, value) ->
                assertThat(environment.getProperty("customer.kafka.topics." + key))
                        .as("%s: %s", yamlPath, key).isEqualTo(value));

        Set<String> subscribedTopics = new HashSet<>();
        for (var method : CustomerKafkaEventConsumer.class.getDeclaredMethods()) {
            KafkaListener listener = method.getAnnotation(KafkaListener.class);
            if (listener == null) {
                continue;
            }
            assertThat(listener.topics()).hasSize(1);
            String topic = environment.resolveRequiredPlaceholders(listener.topics()[0]);
            assertThat(subscribedTopics.add(topic)).as("Unique listener topic: %s", topic).isTrue();
            RetryableTopic retry = method.getAnnotation(RetryableTopic.class);
            assertThat(retry).isNotNull();
            assertThat(retry.dltTopicSuffix()).isEqualTo(".DLT");
        }
        assertThat(subscribedTopics).containsExactlyInAnyOrderElementsOf(EXPECTED_TOPICS.values());
    }
}

package com.chapchap.customer.global.kafka.consumer;

import com.chapchap.customer.global.kafka.event.CustomerKafkaEvent;
import com.chapchap.customer.global.error.custom.kafka.KafkaContractException;
import com.chapchap.customer.global.kafka.service.CustomerKafkaEventProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class CustomerKafkaEventConsumer {
    private final ObjectMapper objectMapper;
    private final CustomerKafkaEventProcessingService processingService;

    @RetryableTopic(attempts = "4", backOff = @BackOff(delay = 1000L), dltTopicSuffix = ".DLT", dltStrategy = DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = "${customer.kafka.topics.payment}", groupId = "${customer.kafka.groups.payment}")
    public void handlePaymentEvent(String payload, org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record) {
        process(record.key(), payload);
    }

    @RetryableTopic(attempts = "4", backOff = @BackOff(delay = 1000L), dltTopicSuffix = ".DLT", dltStrategy = DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = "${customer.kafka.topics.refund}", groupId = "${customer.kafka.groups.refund}")
    public void handleRefundEvent(String payload, org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record) {
        process(record.key(), payload);
    }

    @RetryableTopic(attempts = "4", backOff = @BackOff(delay = 1000L), dltTopicSuffix = ".DLT", dltStrategy = DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = "${customer.kafka.topics.subscription-notification}", groupId = "${customer.kafka.groups.subscription-notification}")
    public void handleSubscriptionNotificationEvent(String payload, org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record) {
        process(record.key(), payload);
    }

    @RetryableTopic(attempts = "4", backOff = @BackOff(delay = 1000L), dltTopicSuffix = ".DLT", dltStrategy = DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = "${customer.kafka.topics.subscription}", groupId = "${customer.kafka.groups.subscription}")
    public void handleSubscriptionEvent(String payload, org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record) {
        process(record.key(), payload);
    }

    @RetryableTopic(attempts = "4", backOff = @BackOff(delay = 1000L), dltTopicSuffix = ".DLT", dltStrategy = DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = "${customer.kafka.topics.delivery-address}", groupId = "${customer.kafka.groups.delivery-address}")
    public void handleDeliveryAddressEvent(String payload, org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record) {
        process(record.key(), payload);
    }

    @RetryableTopic(attempts = "4", backOff = @BackOff(delay = 1000L), dltTopicSuffix = ".DLT", dltStrategy = DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = "${customer.kafka.topics.delivery}", groupId = "${customer.kafka.groups.delivery}")
    public void handleDeliveryEvent(String payload, org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record) {
        process(record.key(), payload);
    }

    @RetryableTopic(attempts = "4", backOff = @BackOff(delay = 1000L), dltTopicSuffix = ".DLT", dltStrategy = DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = "${customer.kafka.topics.delivery-operation-notification}", groupId = "${customer.kafka.groups.delivery-operation-notification}")
    public void handleDeliveryOperationNotificationEvent(String payload, org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record) {
        process(record.key(), payload);
    }

    private void process(String messageKey, String payload) {
        try {
            processingService.process(messageKey, objectMapper.readValue(payload, CustomerKafkaEvent.class));
        } catch (KafkaContractException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new KafkaContractException("Kafka Event 역직렬화에 실패했습니다.");
        }
    }
}

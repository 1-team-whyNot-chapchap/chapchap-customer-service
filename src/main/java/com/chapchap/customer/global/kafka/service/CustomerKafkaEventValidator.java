package com.chapchap.customer.global.kafka.service;

import com.chapchap.customer.global.kafka.event.CustomerKafkaEvent;
import com.chapchap.customer.global.error.custom.kafka.KafkaContractException;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;

@Component
public class CustomerKafkaEventValidator {
    public void validateEnvelope(CustomerKafkaEvent event, boolean operationNotification) {
        if (event == null) {
            throw new KafkaContractException("Kafka Event가 없습니다.");
        }
        validateUuid(event.eventId(), "eventId");
        if (event.eventType() == null || event.eventType().isBlank()) {
            throw new KafkaContractException("eventType은 필수입니다.");
        }
        if (event.version() == null || event.version() != 1) {
            throw new KafkaContractException("지원하지 않는 Event Schema Version입니다.");
        }
        try {
            OffsetDateTime.parse(event.occurredAt());
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new KafkaContractException("occurredAt은 RFC 3339 Offset DateTime이어야 합니다.");
        }
        if (event.data() == null) {
            throw new KafkaContractException("data는 필수입니다.");
        }
        if (operationNotification) {
            if (event.userId() != null) {
                throw new KafkaContractException("운영 알림 Event의 userId는 null이어야 합니다.");
            }
            return;
        }
        if (event.userId() == null || event.userId() <= 0) {
            throw new KafkaContractException("userId는 양의 정수여야 합니다.");
        }
    }

    public String requiredText(CustomerKafkaEvent event, String fieldName) {
        Object value = data(event).get(fieldName);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new KafkaContractException(fieldName + "은 필수 문자열입니다.");
        }
        return text;
    }

    public long requiredPositiveLong(CustomerKafkaEvent event, String fieldName) {
        Object value = data(event).get(fieldName);
        if (!(value instanceof Number number) || number.longValue() <= 0) {
            throw new KafkaContractException(fieldName + "은 양의 정수여야 합니다.");
        }
        return number.longValue();
    }

    public void validateMessageKey(String messageKey, String expectedId) {
        if (messageKey == null || !messageKey.equals(expectedId)) {
            throw new KafkaContractException("Kafka Message Key가 Event 식별자와 일치하지 않습니다.");
        }
    }

    private Map<String, Object> data(CustomerKafkaEvent event) {
        if (event.data() == null) {
            throw new KafkaContractException("data는 필수입니다.");
        }
        return event.data();
    }

    private void validateUuid(String value, String fieldName) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new KafkaContractException(fieldName + "은 UUID여야 합니다.");
        }
    }
}

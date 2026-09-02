package com.chapchap.customer.global.kafka.event;

import java.time.OffsetDateTime;
import java.util.Map;

public record CustomerKafkaEvent(
        String eventId,
        String eventType,
        Integer version,
        String occurredAt,
        Long userId,
        Map<String, Object> data
) {
    public OffsetDateTime occurredAtAsOffsetDateTime() {
        return OffsetDateTime.parse(occurredAt);
    }
}

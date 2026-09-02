package com.chapchap.customer.global.kafka.event;

public enum CustomerKafkaEventType {
    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    PAYMENT_RETRY_STOPPED,
    REFUND_COMPLETED,
    REFUND_FAILED,
    SUBSCRIPTION_SETTING_CHANGED,
    SUBSCRIPTION_CANCELLATION_CONFIRMED,
    SUBSCRIPTION_ENDED,
    SUBSCRIPTION_STATUS_CHANGED,
    DELIVERY_ADDRESS_CHANGED,
    DELIVERY_ADDRESS_CHANGE_REJECTED,
    DELIVERY_CREATED,
    DELIVERY_STARTED,
    DELIVERY_DELAYED,
    DELIVERY_COMPLETED,
    DELIVERY_FAILED,
    DELIVERY_OPERATION_NOTIFICATION_REQUESTED;

    public static CustomerKafkaEventType from(String eventType) {
        try {
            return valueOf(eventType);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }
}

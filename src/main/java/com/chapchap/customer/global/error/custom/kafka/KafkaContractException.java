package com.chapchap.customer.global.error.custom.kafka;

public class KafkaContractException extends RuntimeException {
    public KafkaContractException(String message) {
        super(message);
    }
}

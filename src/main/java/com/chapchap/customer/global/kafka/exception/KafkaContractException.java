package com.chapchap.customer.global.kafka.exception;

public class KafkaContractException extends RuntimeException {
    public KafkaContractException(String message) {
        super(message);
    }
}

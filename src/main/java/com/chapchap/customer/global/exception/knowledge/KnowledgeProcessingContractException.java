package com.chapchap.customer.global.exception.knowledge;

public class KnowledgeProcessingContractException extends RuntimeException {
    public KnowledgeProcessingContractException(String message) {
        super(message);
    }

    public KnowledgeProcessingContractException(String message, Throwable cause) {
        super(message, cause);
    }
}

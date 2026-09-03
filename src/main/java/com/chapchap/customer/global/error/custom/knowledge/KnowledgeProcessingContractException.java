package com.chapchap.customer.global.error.custom.knowledge;

public class KnowledgeProcessingContractException extends RuntimeException {
    public KnowledgeProcessingContractException(String message) {
        super(message);
    }

    public KnowledgeProcessingContractException(String message, Throwable cause) {
        super(message, cause);
    }
}

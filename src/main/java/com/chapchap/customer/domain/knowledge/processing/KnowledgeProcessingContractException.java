package com.chapchap.customer.domain.knowledge.processing;

public class KnowledgeProcessingContractException extends RuntimeException {
    public KnowledgeProcessingContractException(String message) {
        super(message);
    }

    public KnowledgeProcessingContractException(String message, Throwable cause) {
        super(message, cause);
    }
}

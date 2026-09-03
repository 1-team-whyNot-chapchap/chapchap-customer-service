package com.chapchap.customer.domain.knowledge.processing;

public record KnowledgeProcessingResult(
        boolean completed,
        int chunkCount,
        String failureCode,
        boolean retryable
) {
    public static KnowledgeProcessingResult completed(int chunkCount) {
        return new KnowledgeProcessingResult(true, chunkCount, null, false);
    }

    public static KnowledgeProcessingResult failed(String failureCode, boolean retryable) {
        return new KnowledgeProcessingResult(false, 0, failureCode, retryable);
    }
}

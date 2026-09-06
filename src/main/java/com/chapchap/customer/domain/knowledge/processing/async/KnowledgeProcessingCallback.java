package com.chapchap.customer.domain.knowledge.processing.async;

public record KnowledgeProcessingCallback(
        long processingId,
        long knowledgeVersionId,
        Status status,
        Integer chunkCount,
        String chunkProfile,
        FailureCode failureCode,
        Boolean retryable
) {
    public enum Status {
        COMPLETED,
        FAILED
    }

    public enum FailureCode {
        SOURCE_FETCH_FAILED,
        TEXT_EXTRACTION_FAILED,
        UNSUPPORTED_DOCUMENT,
        ENCRYPTED_DOCUMENT,
        CHUNK_PROFILE_INVALID,
        EMBEDDING_UNAVAILABLE,
        VECTOR_STORE_UNAVAILABLE,
        PROCESSING_TIMEOUT,
        CUSTOMER_AI_UNAVAILABLE
    }
}
package com.chapchap.customer.domain.knowledge.processing;

public record KnowledgeProcessingResponse(
        Long knowledgeVersionId,
        String status,
        Integer chunkCount,
        String failureCode,
        Boolean retryable
) {
}

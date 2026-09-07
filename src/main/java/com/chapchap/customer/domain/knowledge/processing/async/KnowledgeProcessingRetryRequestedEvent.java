package com.chapchap.customer.domain.knowledge.processing.async;

public record KnowledgeProcessingRetryRequestedEvent(
        Long knowledgeVersionId,
        int completedAttempt
) {
}

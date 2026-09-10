package com.chapchap.customer.domain.knowledge.dto.processing.async;

public record KnowledgeProcessingRetryRequestedEvent(
        Long knowledgeVersionId,
        int completedAttempt
) {
}

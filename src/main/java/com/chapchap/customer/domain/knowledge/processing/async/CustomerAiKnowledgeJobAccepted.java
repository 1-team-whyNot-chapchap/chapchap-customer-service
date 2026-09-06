package com.chapchap.customer.domain.knowledge.processing.async;

public record CustomerAiKnowledgeJobAccepted(
        long processingId,
        long knowledgeVersionId
) {
}
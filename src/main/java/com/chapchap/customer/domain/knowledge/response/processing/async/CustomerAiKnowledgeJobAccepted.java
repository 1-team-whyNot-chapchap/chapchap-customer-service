package com.chapchap.customer.domain.knowledge.response.processing.async;

public record CustomerAiKnowledgeJobAccepted(
        long processingId,
        long knowledgeVersionId
) {
}
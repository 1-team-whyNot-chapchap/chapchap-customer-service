package com.chapchap.customer.domain.knowledge.processing.async;

import java.util.Objects;
import java.util.UUID;

public record KnowledgeProcessingJobSnapshot(
        UUID requestId,
        long processingId,
        long knowledgeVersionId,
        int attempt,
        String chunkProfile,
        KnowledgeProcessingCallback terminalCallback
) {
    public KnowledgeProcessingJobSnapshot {
        Objects.requireNonNull(requestId);
        if (processingId <= 0 || knowledgeVersionId <= 0 || attempt < 1 || attempt > 3) {
            throw new IllegalArgumentException("Knowledge job identity is invalid.");
        }
        if (!CustomerAiKnowledgeJobCommand.SUPPORTED_CHUNK_PROFILE.equals(chunkProfile)) {
            throw new IllegalArgumentException("Knowledge job chunkProfile is invalid.");
        }
    }
}
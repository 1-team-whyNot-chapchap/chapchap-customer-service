package com.chapchap.customer.domain.knowledge.response;

import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;

import java.time.LocalDateTime;

public record KnowledgeVersionResponse(
        Long knowledgeVersionId,
        Long knowledgeDocumentId,
        String version,
        String chunkProfile,
        String processingStatus,
        boolean active,
        String failureCode,
        boolean retryable,
        int processingAttemptCount,
        LocalDateTime effectiveFrom,
        LocalDateTime processedAt,
        LocalDateTime activatedAt
) {
    public static KnowledgeVersionResponse from(KnowledgeVersion knowledgeVersion) {
        return new KnowledgeVersionResponse(
                knowledgeVersion.getId(),
                knowledgeVersion.getKnowledgeDocumentId(),
                knowledgeVersion.getVersion(),
                knowledgeVersion.getChunkProfile(),
                knowledgeVersion.getProcessingStatus().name(),
                knowledgeVersion.isActive(),
                knowledgeVersion.getFailureCode(),
                knowledgeVersion.isRetryable(),
                knowledgeVersion.getProcessingAttemptCount(),
                knowledgeVersion.getEffectiveFrom(),
                knowledgeVersion.getProcessedAt(),
                knowledgeVersion.getActivatedAt()
        );
    }
}

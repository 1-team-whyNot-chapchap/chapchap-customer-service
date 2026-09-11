package com.chapchap.customer.domain.knowledge.dto.processing;

import java.time.LocalDateTime;

public record KnowledgeProcessingContext(
        Long knowledgeVersionId,
        int attempt,
        String objectKey,
        String contentType,
        long fileSize,
        String documentKey,
        String sourceService,
        String category,
        String version,
        LocalDateTime effectiveFrom,
        String chunkProfile
) {
}
